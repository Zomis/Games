package net.zomis.games.impl.alchemists

import net.zomis.games.api.GamesApi
import net.zomis.games.cards.CardZone
import net.zomis.games.common.next
import kotlin.random.Random

class AlchemistsModel(playerCount: Int, val config: Config) {

    data class Hero(val requests: List<AlchemistsPotion>)
    data class Config(val master: Boolean)
    enum class ActionUnit { CUBE, ASSOCIATE }
    enum class ActionType {
        FORAGE,
        TRANSMUTE,
        CUSTODIAN,
        SELL_POTION,
        BUY_ARTIFACT,
        DEBUNK_THEORY,
        MAKE_THEORY,
        TEST_STUDENT,
        TEST_SELF,
        EXHIBIT,
        ;
    }
    enum class FavorType(val count: Int) {
        ASSISTANT(4),
        HERBALIST(4),
        ASSOCIATE(3),
        CUSTODIAN(3),
        SHOPKEEPER(2),
        BARMAID(2),
        MERCHANT(2),
        SAGE(2),
        ;
    }

    class Player(val playerIndex: Int) {
        val favors = CardZone<FavorType>()
        val ingredients = CardZone<Alchemists.Ingredient>()
    }

    fun draftingRule(
        vararg counts: Int,
        specialCondition: ActionDrafting.SpacePlacementScope<ActionType, ActionUnit>.() -> Boolean
    ): SpacePlacementRule<ActionType, ActionUnit> {
        // TODO: Use the counts parameter
        return {
            if (!specialCondition(this)) ActionDrafting.PlacementResult.REJECTED
            else {
                val ok = space.placementsByPlayer(playerIndex).count() <= if (playerCount == 4) 2 else 3
                if (ok) ActionDrafting.PlacementResult.ACCEPTED else ActionDrafting.PlacementResult.REJECTED
            }
        }
    }

    var firstPlayer: Int = 0
    lateinit var solution: Alchemists.AlchemistsSolution
    val heroes = CardZone<Hero>()
    val ingredientDeck = CardZone<Alchemists.Ingredient>()
    val ingredientDiscard = CardZone<Alchemists.Ingredient>()
    val favorDeck = CardZone<FavorType>()
    val players = (0 until playerCount).map { Player(it) }
    val playerCount: Int get() = players.size

    private val forageSpaces = if (playerCount == 4) intArrayOf(1, 1) else intArrayOf(1, 1, 1)
    private val exhibitSpaces = if (playerCount == 4) intArrayOf(1, 1, 1) else intArrayOf(1, 1, 1, 1)
    private val allow: ActionDrafting.SpacePlacementScope<ActionType, ActionUnit>.() -> Boolean = { true }
    val actionPlacements = ActionDrafting.Drafting(listOf(
        ActionDrafting.Space(ActionType.FORAGE, draftingRule(*forageSpaces, specialCondition = allow)),
        ActionDrafting.Space(ActionType.TRANSMUTE, draftingRule(1, 2, specialCondition = allow)),
        ActionDrafting.Space(ActionType.CUSTODIAN, draftingRule(1, 1, 1, 1, specialCondition = {
            players[playerIndex].favors.cards.count { it == FavorType.CUSTODIAN } >= space.placementsByPlayer(playerIndex).size
        })),
        ActionDrafting.Space(ActionType.SELL_POTION, draftingRule(2, specialCondition = { round > 1 })),
        ActionDrafting.Space(ActionType.BUY_ARTIFACT, draftingRule(1, 2, specialCondition = allow)),
        ActionDrafting.Space(ActionType.DEBUNK_THEORY, draftingRule(1, 1, specialCondition = allow)),
        ActionDrafting.Space(ActionType.MAKE_THEORY, draftingRule(1, 2, specialCondition = allow)),
        ActionDrafting.Space(ActionType.TEST_STUDENT, draftingRule(1, 1, specialCondition = { round < 6 })),
        ActionDrafting.Space(ActionType.TEST_SELF, draftingRule(1, 1, specialCondition = { round < 6 })),
        ActionDrafting.Space(ActionType.EXHIBIT, draftingRule(*exhibitSpaces, specialCondition = { round == 6 }))
    ))
    var round: Int = 1

}

object AlchemistsGame {

    val factory = GamesApi.gameCreator(AlchemistsModel::class)

    val game = factory.game("Alchemists") {
        setup(AlchemistsModel.Config::class) {
            defaultConfig { AlchemistsModel.Config(false) }
            players(2..4)
            init { AlchemistsModel(playerCount, config) }
            onStart {
                // Setup solution
                val game = it
                val solutionStrings = this.strings("solution") {
                    Alchemists.alchemyValues.shuffled().map { it.representation }
                }
                val solution = solutionStrings.map { str -> Alchemists.alchemyValues.first { it.representation == str } }
                val ingredients = Alchemists.Ingredient.values()
                game.solution = Alchemists.AlchemistsSolution(solution.withIndex().associate { ingredients[it.index] to it.value })

                // Setup favors
                AlchemistsModel.FavorType.values().forEach {favor ->
                    repeat(favor.count) {
                        game.favorDeck.cards.add(favor)
                    }
                }

                // Setup ingredients
                Alchemists.Ingredient.values().forEach {ingredient ->
                    repeat(8) {
                        game.ingredientDeck.cards.add(ingredient)
                    }
                }
                val startingIngredients = if (game.config.master) 2 else 3
                val startingPlayerIngredients = game.ingredientDeck.random(this, startingIngredients * game.players.size, "ingredients") { it.name }
                    .map { it.card }.toList()
                game.ingredientDeck.deal(startingPlayerIngredients, game.players.map { it.ingredients })

                // TODO: Setup artifacts
                game.firstPlayer = this.int("startingPlayer") { Random.Default.nextInt(game.players.size) }

                // Setup Heroes
                game.heroes.cards.add(AlchemistsModel.Hero(listOf(Alchemists.red.plus, Alchemists.green.plus, Alchemists.blue.plus)))
                game.heroes.cards.add(AlchemistsModel.Hero(listOf(Alchemists.red.minus, Alchemists.green.minus, Alchemists.blue.minus)))
                game.heroes.cards.add(AlchemistsModel.Hero(listOf(Alchemists.red.minus, Alchemists.green.minus, Alchemists.blue.plus)))
                game.heroes.cards.add(AlchemistsModel.Hero(listOf(Alchemists.red.plus, Alchemists.green.minus, Alchemists.blue.minus)))
                game.heroes.cards.add(AlchemistsModel.Hero(listOf(Alchemists.red.plus, Alchemists.green.plus, Alchemists.blue.minus)))
                game.heroes.cards.add(AlchemistsModel.Hero(listOf(Alchemists.red.minus, Alchemists.green.plus, Alchemists.blue.plus)))
                game.heroes.random(this, 5, "heroes") { it.requests.map { req -> req.textRepresentation }.joinToString("") }
            }
        }
        gameFlow {
        }
        gameFlowRules {

        }
    }
}
