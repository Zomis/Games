package net.zomis.games.impl.alchemists

import net.zomis.games.api.GamesApi
import net.zomis.games.common.PlayerIndex
import net.zomis.games.common.Players
import net.zomis.games.common.next
import net.zomis.games.components.resources.GameResource
import net.zomis.games.components.resources.ResourceChange
import net.zomis.games.components.resources.ResourceMap
import net.zomis.games.context.ActivePhases
import net.zomis.games.context.Context
import net.zomis.games.context.ContextHolder
import net.zomis.games.context.Entity
import net.zomis.games.dsl.ActionType
import net.zomis.games.dsl.GameConfig
import net.zomis.games.dsl.GameSerializable
import net.zomis.games.dsl.flow.ActionDefinition
import net.zomis.games.dsl.flow.actions.SmartActionBuilder
import net.zomis.games.impl.GameStack
import net.zomis.games.rules.RuleSpec
import net.zomis.games.rules.StandaloneStateOwner
import net.zomis.games.rules.StateOwner
import kotlin.random.Random

object AlchemistsDelegationGame {
    interface HasAction {
        val actionSpace: Model.ActionSpace
        val action: ActionDefinition<Model, *>
        fun actionAvailable(playerIndex: Int, chosen: List<Model.ActionChoice>): Boolean? = true
        fun extraActions(): List<ActionDefinition<Model, *>> = emptyList()
        fun extraHandlers(): List<Pair<ActionType<Model, Any>, SmartActionBuilder<Model, Any>>> = emptyList()
    }
    enum class Resources : GameResource {
        Gold,
        Favors,
        Ingredients,
        Reputation,
        VictoryPoints,
    }
    sealed interface StackItem {
        val ruleSpec: RuleSpec<Model, Unit>

    }
    val actionPlaceType = GamesApi.gameCreator(Model::class).action("action", Model.ActionPlacement::class)
        .serialization<List<String>>({ it.serialize() }, { deserialized ->
            Model.ActionPlacement(
                deserialized.map { s ->
                    val (associate, count, name) = s.split("/")
                    val spot = game.actionSpaces.first { it.actionSpace.name == name }
                    Model.ActionChoice(spot, count.toInt(), associate == "true")
                }
            )
        })

    class TurnOrderChoice(val player: Model.Player, val turnOrder: Model.TurnOrder, var resources: ResourceMap)
    class Model(override val ctx: Context, master: GameConfig<Boolean>) : Entity(ctx), ContextHolder {
        val stack: GameStack<StackItem> = GameStack()
        var phase = ActivePhases(Phases.phases(this))

        var currentActionSpace: HasAction? = null
        val master by value { false }.setup { config(master) }
        val chosenTurnOrder = event<TurnOrderChoice>()
        val newRound = event<Int>()
        val gameInit = event<Unit>()
        val spaceDone = event<HasAction>()
        var round by value { 0 }.changeOn(newRound) { event }
        val playerMixPotion = event<PotionActions.IngredientsMix>()
        val solution by component { emptyList<Ingredient>() }
            .setup {
                val list = Ingredient.values().toList()
                replayable.randomFromList("solution", list.shuffled(), list.size, Ingredient::toString)
            }
        val alchemySolution get() = Alchemists.solutionWith(solution)
        val baseRule by rule<Model, Unit>(Unit) {
            AlchemistRules.baseRule.invoke(this)
        }
        data class TurnOrder(
            val gold: Int, val favors: Int, val ingredients: Int,
            val choosable: Boolean = true, var chosenBy: Int? = null
        ): GameSerializable {
            val resources = ResourceMap.of(Resources.Gold to gold, Resources.Favors to favors, Resources.Ingredients to ingredients)

            val key = toStateString()
            fun toStateString(): String = "$gold/$favors/$ingredients-$choosable"
            override fun serialize(): Any = this.toStateString()
        }

        inner class TurnPicker(ctx: Context): Entity(ctx) {
            val options by component { listOf<TurnOrder>() }.setup {
                buildList {
                    add(TurnOrder(-1, 0, 0))
                    add(TurnOrder(0, 0, 0))
                    add(TurnOrder(0, 0, 1))
                    add(TurnOrder(0, 0, 2))
                    add(TurnOrder(0, 1, 1))
                    add(TurnOrder(0, 2, 0))
                    if (playerCount == 4) add(TurnOrder(0, 0, 3))
                    add(TurnOrder(0, 1, 2, choosable = playerCount >= 3))
                    add(TurnOrder(0, 1, 1, false))
                }
            }.on(newRound) { value.filter { it.choosable }.forEach { it.chosenBy = null } }
            val actionable by this.viewOnly<Model> {
                this.actionRaw(action.actionType).nextSteps(TurnOrder::class).associate { it.key to true }
            }
            val action = actionSerializable<Model, TurnOrder>("turn", TurnOrder::class) {
                precondition {
                    playerIndex == Players.startingWith(this@Model.players.indices.toList(), this@Model.startingPlayer).minus(
                        options.map { it.chosenBy }.toSet()
                    ).firstOrNull()
                }
                options { turnPicker.options.filter { it.choosable && it.chosenBy == null } }
                requires { players[playerIndex].gold >= -action.parameter.gold }
                perform {
                    val player = players[playerIndex]
                    game.chosenTurnOrder.invoke(TurnOrderChoice(player, action.parameter, action.parameter.resources)) {
                        action.parameter.chosenBy = playerIndex

                        players[playerIndex].gold += action.parameter.resources.getOrDefault(Resources.Gold)
                        val ingredients = action.parameter.resources.getOrDefault(Resources.Ingredients)
                        val favors = action.parameter.resources.getOrDefault(Resources.Favors)
                        game.ingredients.deck.randomWithRefill(game.ingredients.discardPile, replayable, ingredients, "ingredients") { it.serialize() }.forEach {
                            it.moveTo(game.players[playerIndex].ingredients)
                        }
                        game.favors.deck.randomWithRefill(game.favors.discardPile, replayable, favors, "favors") { it.serialize() }
                            .forEach { game.favors.giveFavor(game, it, game.players[playerIndex]) }
                    }
                    log { "$player chose turn order ${action.toStateString()}" }
                }
            }
        }
        val turnPicker by component { TurnPicker(ctx) }

        data class PlayerActionCube(val cubes: Int, var used: Boolean = false)
        class PlayerActionRow(val playerIndex: Int, val cubes: MutableList<PlayerActionCube> = mutableListOf()) {
            fun useNext(): Int {
                val usedCubes = cubes.withIndex().first { it.value.used.not() }.value
                usedCubes.used = true
                return usedCubes.cubes
            }
        }

        inner class ActionSpace(ctx: Context, val name: String, val cost: Favors.FavorType? = null): Entity(ctx) {
            val spaces by component { mutableListOf<Int>() }
            val rows by component { mutableListOf<PlayerActionRow?>() }
                .on(newRound) { value.indices.forEach { value[it] = null } }

            fun initialize(count: List<Int>, playerCount: Int): ActionSpace {
                spaces.addAll(count)
                rows.addAll((0 until playerCount).map { null })
                return this
            }

            fun nextCost(chosen: List<ActionChoice>): Int? {
                val spacesLeft = chosen.filter { it.spot.actionSpace == this }.map { it.count }.fold(spaces) { acc, next -> acc.minusElement(next).toMutableList() }
                return spacesLeft.firstOrNull()
            }

            fun place(playerIndex: Int, choice: List<ActionChoice>) {
                val cubes = choice.map { it.count }
                val spacesLeft = this.spaces.toMutableList()
                check(spacesLeft.take(cubes.size) == cubes)
                if (choice.any { it.associate }) {
                    rows.add(0, PlayerActionRow(playerIndex, cubes.map { PlayerActionCube(it) }.toMutableList()))
                    rows.removeAt(rows.indexOf(null))
                } else {
                    val rowIndex = rows.lastIndexOf(null)
                    rows[rowIndex] = PlayerActionRow(playerIndex, cubes.map { PlayerActionCube(it) }.toMutableList())
                }
            }

            private fun nextIndex(): Pair<Int, Int>? {
                for (index in 0..rows.maxOf { it?.cubes?.size ?: 0 }) {
                    for (rowIndex in rows.indices) {
                        val row = rows[rowIndex]
                        if (row != null && row.cubes.size > index && !row.cubes[index].used) {
                            return rowIndex to index
                        }
                    }
                }
                return null
            }
            fun next(): Pair<PlayerIndex, Int>? { // player, cubes
                val index = nextIndex() ?: return null
                val (rowIndex, actionListIndex) = index
                val row = rows[rowIndex]!!
                return row.playerIndex to row.cubes[actionListIndex].cubes
            }

            fun resolveNext(playerIndex: Int): Int {
                val list = rows.first { it?.playerIndex == playerIndex }!!
                return list.useNext()
            }
            fun has(playerIndex: Int): Boolean {
                val list = rows.find { it?.playerIndex == playerIndex }?.cubes ?: return false
                return list.any { !it.used }
            }
            fun resolveNext(): Int {
                val index = nextIndex()!!
                val (rowIndex, actionListIndex) = index
                val row = rows[rowIndex]!!
                return row.useNext()
            }

            fun nextPlayerIndex(): Int? = this.next()?.first
        }

        inner class Player(val model: Model, ctx: Context, val playerIndex: Int): Entity(ctx) {
            val stateOwner: StateOwner = StandaloneStateOwner()
            val resourceChange = event<ResourceChange>()
            val resources by component { ResourceMap.of(Resources.Gold to 2, Resources.Reputation to 10).toMutableResourceMap(resourceChange) }.publicView { it.toView() }
            var gold by resource(resources, Resources.Gold)
            var reputation by resource(resources, Resources.Reputation)
            var extraCubes by component { 0 }
            var artifacts by cards<ArtifactActions.Artifact>().publicView { it.cards }
            val favors by cards<Favors.FavorType>().privateView(playerIndex) { it.cards }.publicView { it.size }
                .setup { myFavors ->
                    model.favors.deck.random(replayable, 2, "favors-$playerIndex") { it.serialize() }.forEach { it.moveTo(myFavors) }
                    myFavors
                }
            val seals by cards<TheoryActions.Seal>().privateView(playerIndex) { it.cards }
            val actionCubesAvailable by dynamicValue { this@Model.actionCubeCount + extraCubes }
            val ingredients by cards<Ingredient>()
                .on(gameInit) {
                    val startingIngredients = if (master) 2 else 3
                    model.ingredients.deck.random(replayable, startingIngredients, "startingIngredients-$playerIndex") { it.serialize() }
                        .forEach { it.moveTo(value) }
                }
                .on(playerMixPotion) {
                    if (playerIndex == event.playerIndex) {
                        val discardIndices = if (artifacts.cards.contains(ArtifactActions.magicMortar))
                                listOf(replayable.int("discardIndex") { Random.Default.nextInt(0, 2) })
                            else listOf(0, 1)
                        discardIndices.forEach {
                            value.card(event.ingredients.toList()[it]).moveTo(model.ingredients.discardPile)
                        }
                    }
                }
                .privateView(playerIndex) { it.cards.map { i -> i.serialize() } }
                .publicView { it.size }
        }

        val players by playerComponent { Player(this@Model, ctx, it) }
        val startingPlayer by playerReference { 0 }
            .setup { replayable.int("startingPlayer") { playerIndices.random() } }
            .on(newRound) { value.next(players.size) }

        data class ActionChoice(val spot: HasAction, val count: Int, val associate: Boolean)
        data class ActionPlacement(val chosen: List<ActionChoice>): GameSerializable {
            override fun serialize(): List<String> = chosen.map { "${it.associate}/${it.count}/${it.spot.actionSpace.name}" }
        }
        fun nextActionPlacer(): Int? = turnPicker.options.lastOrNull { it.chosenBy != null }?.chosenBy
        val actionPlacement = action<Model, ActionPlacement>(actionPlaceType) {
            precondition {
                playerIndex == nextActionPlacer()
            }
            requires { action.parameter.chosen.sumOf { it.count } <= players[playerIndex].actionCubesAvailable }
            requires { action.parameter.chosen.groupBy { it.spot }.values.all { it.count { c -> c.associate } <= 1 } }
            requires { players[playerIndex].favors.cards.count { it == Favors.FavorType.CUSTODIAN } >= action.parameter.chosen.count { it.spot == custodian } }
            requires { action.parameter.chosen.all { it.spot.actionAvailable(playerIndex, action.parameter.chosen) != false } }
            perform {
                for (i in 1..action.parameter.chosen.count { it.spot == custodian }) {
                    players[playerIndex].favors.card(Favors.FavorType.CUSTODIAN).moveTo(favors.discardPile)
                }
                action.parameter.chosen.groupBy { it.spot }.forEach {
                    it.key.actionSpace.place(playerIndex, it.value)
                }
                turnPicker.options.first { it.chosenBy == playerIndex }.chosenBy = null
                log { "$player chose actions ${action.chosen.map { it.spot.actionSpace.name }.sorted()}" }
            }
            choose {
                recursive(emptyList<ActionChoice>()) {
                    parameter { ActionPlacement(chosen) }
                    until { this.chosen.sumOf { it.count } == players[playerIndex].actionCubesAvailable }
                    optionsWithIds({ actionSpaces.map {
                        it.actionSpace.name to (it to it.actionSpace.nextCost(chosen))
                    }.filter {
                        it.second.second != null && it.second.second!! <= players[playerIndex].actionCubesAvailable - chosen.sumOf { c -> c.count }
                    }.filter { it.second.first.actionAvailable(playerIndex, chosen) == true } }) { next ->
                        if (game.players[playerIndex].favors.cards.contains(Favors.FavorType.ASSOCIATE)) {
                            optionsWithIds({ listOf("associate" to true, "no associate" to false) }) { useAssociate ->
                                recursion(next) { acc, n -> acc + ActionChoice(n.first, n.second!!, useAssociate) }
                            }
                        } else {
                            recursion(next) { acc, n -> acc + ActionChoice(n.first, n.second!!, false) }
                        }
                    }
                }
            }
        }
        val actionCubeCount by component { 3 }.changeOn(newRound) {
            when {
                event == 1 -> 3
                players.size == 2 -> 6
                players.size == 3 -> 5
                else -> 4
            }
        }

        val favors by component { Favors.FavorDeck(ctx) }
        val ingredients by component { IngredientActions.Ingredients(this@Model, ctx) }
        val transmute by component { IngredientActions.Transmute(this@Model, ctx) }
        val custodian by component { PotionActions.Custodian(this@Model, ctx) }
        val sellPotion by component { SellAction.SellHero(this@Model, ctx) }
        val buyArtifact by component { ArtifactActions.BuyArtifact(this@Model, ctx) }
        val debunkTheory by component { TheoryActions.DebunkTheory(this@Model, ctx) }
        val publishTheory by component { TheoryActions.PublishTheory(this@Model, ctx) }
        val testStudent by component { PotionActions.TestStudent(this@Model, ctx) }
        val testSelf by component { PotionActions.TestSelf(this@Model, ctx) }
        val exhibition by component { PotionActions.Exhibition(this@Model, ctx) }
        val theoryBoard by component { TheoryActions.TheoryBoard(this@Model, ctx) }
        val cancelledActions by value { mutableListOf<Int>() }
        fun cancelAction(space: HasAction) = action<Model, Unit>("cancel", Unit::class) {
            precondition { playerIndex == space.actionSpace.nextPlayerIndex() }
            perform {
                val count = space.actionSpace.resolveNext()
                for (i in 1..count) cancelledActions.add(playerIndex)
                favors.favorsPlayed.moveAllTo(favors.discardPile)
                log { "$player cancelled their action" }
            }
        }

        val actionSpaces = listOf<HasAction>(
            ingredients, transmute, custodian, sellPotion, buyArtifact,
            debunkTheory, publishTheory, testStudent, testSelf,
            exhibition
        )
    }

    val game = GamesApi.gameContext("Alchemists", Model::class) {
        val master = config("master") { false }
        players(2..4)
        init { Model(this.ctx, master) }
        baseRule(Model::baseRule)
        with(AlchemistTests) {
            tests()
        }
    }

}
