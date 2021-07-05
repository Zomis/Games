package net.zomis.games.impl.alchemists

import net.zomis.games.api.Games
import net.zomis.games.api.GamesApi
import net.zomis.games.cards.CardZone
import net.zomis.games.common.next
import net.zomis.games.impl.alchemists.artifacts.*
import net.zomis.games.dsl.flow.GameFlowScope
import kotlin.random.Random

class AlchemistsModel(val playerCount: Int, val config: Config) {

    data class Hero(val requests: List<AlchemistsPotion>)
    data class Config(val master: Boolean) {

        fun turnOrders(playerCount: Int): List<TurnOrder> {
            return sequence<TurnOrder> {
                yield(TurnOrder(1, 0, 0))
                yield(TurnOrder(0, 0, 0))
                yield(TurnOrder(0, 0, 1))
                yield(TurnOrder(0, 0, 2))
                yield(TurnOrder(0, 1, 1))
                yield(TurnOrder(0, 2, 0))
                if (playerCount == 4) {
                    yield(TurnOrder(0, 0, 3))
                }
                if (playerCount >= 3) {
                    yield(TurnOrder(0, 1, 2))
                }
                yield(TurnOrder(0, 1, 1, false))
            }.toList()
        }
    }
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

    fun allArtifacts(): List<Artifact> {
        return listOf(
                AltarOfGold,
                AmuletOfRhetoric,
                BootsOfSpeed,
                BronzeCup,
                CrystalCabinet,
                DiscountCard,
                FeatherInCap,
                HypnoticAmulet,
                MagicMirror,
                MagicMortar,
                Periscope,
                PrintingPress,
                RobeOfRespect,
                SealOfAuthority,
                SilverChalice,
                ThinkingCap,
                WisdomIdol,
                WitchsTrunk
        )
    }

    fun selectArtifacts(artifacts: List<Artifact>): List<Artifact> {
        return artifacts.groupBy { it.level }.map { it.value.shuffled().take(3) }.flatten()
    }

    class Player(val playerIndex: Int) {
        var gold: Int = 0
        var reputation: Int = 0
        val favors = CardZone<FavorType>()
        val ingredients = CardZone<Alchemists.Ingredient>()

        fun victoryPoints(): Int {
            return reputation
        }
    }

    fun draftingRule(
        vararg counts: Int,
        specialCondition: ActionDrafting.SpacePlacementScope<ActionType, ActionUnit>.() -> Boolean
    ): SpacePlacementRule<ActionType, ActionUnit> {
        // TODO: Use the counts parameter
        return {
            if (!specialCondition(this)) false
            else {
                space.placementsByPlayer(playerIndex).count() <= if (playerCount == 4) 2 else 3
            }
        }
    }

    var firstPlayer: Int = 0
    lateinit var solution: Alchemists.AlchemistsSolution
    val heroes = Games.components.cardZone<Hero>()
    val artifacts = CardZone<Artifact>()
    val ingredientDeck = CardZone<Alchemists.Ingredient>()
    val ingredientDiscard = CardZone<Alchemists.Ingredient>()
    val favorDeck = CardZone<FavorType>()
    val players = (0 until playerCount).map { Player(it) }

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

    data class TurnOrder(val goldCost: Int, val favors: Int, val ingredients: Int, val choosable: Boolean = true)
    private val turnOrderPlacementRule: SpacePlacementRule<TurnOrder, Unit> = {
        if (players[playerIndex].gold < zone.goldCost) false
        else zone.choosable
    }
    val turnOrderPlacements = ActionDrafting.Drafting(config.turnOrders(playerCount).map { ActionDrafting.Space(it, turnOrderPlacementRule) })

}

object AlchemistsGame {

    val factory = Games.api.gameCreator(AlchemistsModel::class)
    val turnOrder = factory.action("turnOrder", AlchemistsModel.TurnOrder::class)

    class AlchemistsActionChoice(val playerIndex: Int, val assistants: Int, val placements: List<ActionDrafting.Placement<AlchemistsModel.ActionType, Boolean>>)

    val game = factory.game("Alchemists") {
        setup(AlchemistsModel.Config::class) {
            defaultConfig { AlchemistsModel.Config(false) }
            players(2..4)
            init { AlchemistsModel(playerCount, config) }
            onStart {
                val solution = replayable.randomFromList("solution", Alchemists.alchemyValues, Alchemists.alchemyValues.size) { it.representation }
                val ingredients = Alchemists.Ingredient.values()
                game.solution = Alchemists.AlchemistsSolution(solution.withIndex().associate { ingredients[it.index] to it.value })

                // Setup favors
                AlchemistsModel.FavorType.values().forEach { favor ->
                    repeat(favor.count) {
                        game.favorDeck.cards.add(favor)
                    }
                }

                // Setup ingredients
                Alchemists.Ingredient.values().forEach { ingredient ->
                    repeat(8) {
                        game.ingredientDeck.cards.add(ingredient)
                    }
                }
                val startingIngredients = if (game.config.master) 2 else 3
                val startingPlayerIngredients = game.ingredientDeck.random(replayable, startingIngredients * game.players.size, "ingredients") { it.name }
                        .map { it.card }.toList()
                game.ingredientDeck.deal(startingPlayerIngredients, game.players.map { it.ingredients })

                // TODO: Setup artifacts
                game.artifacts.cards.addAll(
                        replayable.strings("artifacts") { game.selectArtifacts(game.allArtifacts()).map { it.name } }
                                .map { name -> game.allArtifacts().first { it.name == name } }
                )

                game.firstPlayer = replayable.int("startingPlayer") { Random.Default.nextInt(game.players.size) }

                game.players.forEach {
                    it.gold = 2
                    it.reputation = 10
                }

                // Setup Heroes
                game.heroes.cards.add(AlchemistsModel.Hero(listOf(Alchemists.red.plus, Alchemists.green.plus, Alchemists.blue.plus)))
                game.heroes.cards.add(AlchemistsModel.Hero(listOf(Alchemists.red.minus, Alchemists.green.minus, Alchemists.blue.minus)))
                game.heroes.cards.add(AlchemistsModel.Hero(listOf(Alchemists.red.minus, Alchemists.green.minus, Alchemists.blue.plus)))
                game.heroes.cards.add(AlchemistsModel.Hero(listOf(Alchemists.red.plus, Alchemists.green.minus, Alchemists.blue.minus)))
                game.heroes.cards.add(AlchemistsModel.Hero(listOf(Alchemists.red.plus, Alchemists.green.plus, Alchemists.blue.minus)))
                game.heroes.cards.add(AlchemistsModel.Hero(listOf(Alchemists.red.minus, Alchemists.green.plus, Alchemists.blue.plus)))
                game.heroes.random(replayable, 5, "heroes") { it.requests.map { req -> req.textRepresentation }.joinToString("") }
            }
        }
        gameFlow {
            for (round in 1..6) {
                game.round = round
                chooseTurnOrderPhase(this)
                chooseActionsPhase(this)
                for (actions in game.actionPlacements.spaces) {
                    if (actions.zone == AlchemistsModel.ActionType.SELL_POTION) {
                        chooseDiscountPhase(this, actions) // Required for selling to hero, if multiple players are selling
                    }
                    performActionPhase(this, actions)
                }
                endRound(this)
                game.firstPlayer = game.firstPlayer.next(game.playerCount)
            }
            step("eliminations") {
                eliminations.eliminateBy(game.players.map { it.playerIndex to it.victoryPoints() }, compareBy { it })
            }
        }
        gameFlowRules {

        }
        AlchemistsTests.tests(this)
    }

    private suspend fun endRound(flow: GameFlowScope<AlchemistsModel>) {
        when (flow.game.round) {
            3 -> {}
            5 -> {}
            else -> {}
        }
    }

    private suspend fun performActionPhase(flow: GameFlowScope<AlchemistsModel>, actions: ActionDrafting.Space<AlchemistsModel.ActionType, AlchemistsModel.ActionUnit>) {

    }

    private suspend fun chooseDiscountPhase(flow: GameFlowScope<AlchemistsModel>, actions: ActionDrafting.Space<AlchemistsModel.ActionType, AlchemistsModel.ActionUnit>) {

    }

    private suspend fun chooseActionsPhase(flow: GameFlowScope<AlchemistsModel>) {

    }

    private suspend fun chooseTurnOrderPhase(flow: GameFlowScope<AlchemistsModel>) {
        val firstPlayer = flow.game.firstPlayer
        for (i in (firstPlayer until firstPlayer + flow.game.playerCount)) {
            val playerIndex = i % flow.game.playerCount
            flow.step("Choose turn order - $playerIndex") {
                yieldAction(turnOrder) {
                    precondition { this.playerIndex == playerIndex }
                    options {
                        game.turnOrderPlacements.zoneOptions(this.playerIndex, listOf(Unit)).map { it.space.zone }
                    }
                    requires {
                        game.turnOrderPlacements.allowed(action.playerIndex, action.parameter, Unit)
                    }
                    perform {
                        game.turnOrderPlacements.makePlacement(action.playerIndex, action.parameter, Unit)
                    }
                }
            }
        }
    }
}
