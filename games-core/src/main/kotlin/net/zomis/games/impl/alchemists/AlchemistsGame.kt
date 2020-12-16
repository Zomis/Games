package net.zomis.games.impl.alchemists

import net.zomis.games.api.GamesApi
import net.zomis.games.cards.CardZone
import net.zomis.games.common.next
import kotlin.random.Random

class AlchemistsModel(playerCount: Int, val config: Config) {

    data class Hero(val requests: List<AlchemistsPotion>)
    data class Config(val master: Boolean)
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
        val ingredients = CardZone<Alchemists.Ingredient>()
    }

    var firstPlayer: Int = 0
    lateinit var solution: Alchemists.AlchemistsSolution
    val heroes = CardZone<Hero>()
    val ingredientDeck = CardZone<Alchemists.Ingredient>()
    val ingredientDiscard = CardZone<Alchemists.Ingredient>()
    val favorDeck = CardZone<FavorType>()
    val players = (0 until playerCount).map { Player(it) }
    val playerCount: Int get() = players.size

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
