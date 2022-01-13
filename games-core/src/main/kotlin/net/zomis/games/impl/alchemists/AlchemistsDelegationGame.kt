package net.zomis.games.impl.alchemists

import net.zomis.games.api.GamesApi
import net.zomis.games.common.PlayerIndex
import net.zomis.games.common.Players
import net.zomis.games.common.next
import net.zomis.games.context.Context
import net.zomis.games.context.ContextHolder
import net.zomis.games.context.Entity
import net.zomis.games.dsl.GameConfig
import net.zomis.games.dsl.GameSerializable
import net.zomis.games.dsl.flow.ActionDefinition
import net.zomis.games.dsl.flow.GameFlowScope
import kotlin.random.Random

object AlchemistsDelegationGame {
    interface HasAction {
        val actionSpace: Model.ActionSpace
        val action: ActionDefinition<Model, *>
        fun actionAvailable(playerIndex: Int, chosen: List<Model.ActionChoice>): Boolean? = true
        fun extraActions(): List<ActionDefinition<Model, *>> = emptyList()
    }
    class Model(override val ctx: Context, master: GameConfig<Boolean>) : Entity(ctx), ContextHolder {
        val master by value { false }.setup { config(master) }
        val queue by component { mutableListOf<ActionDefinition<Model, Any>>() }
        val newRound by event(Int::class)
        val gameInit by event(Unit::class)
        val spaceDone by event(HasAction::class)
        var round by value { 0 }.changeOn(newRound) { event }
        val playerMixPotion by event(PotionActions.IngredientsMix::class)
        val solution by component { emptyList<Ingredient>() }
            .setup {
                val list = Ingredient.values().toList()
                replayable.randomFromList("solution", list.shuffled(), list.size, Ingredient::toString)
            }
        val alchemySolution get() = Alchemists.solutionWith(solution)
        inner class TurnOrder(
            val gold: Int, val favors: Int, val ingredients: Int,
            val choosable: Boolean = true, var chosenBy: Int? = null
        ): GameSerializable {
            fun toStateString(): String = "$gold/$favors/$ingredients"
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
                    if (playerCount >= 3) add(TurnOrder(0, 1, 2))
                    add(TurnOrder(0, 1, 1, false))
                }
            }.on(newRound) { value.filter { it.choosable }.forEach { it.chosenBy = null } }
            val action by actionSerializable<Model, TurnOrder>("turn", TurnOrder::class) {
                precondition {
                    playerIndex == Players.startingWith(this@Model.players.indices.toList(), this@Model.startingPlayer).minus(
                        options.map { it.chosenBy }.toSet()
                    ).first()
                }
                options { turnPicker.options.filter { it.choosable && it.chosenBy == null } }
                requires { players[playerIndex].gold >= -action.parameter.gold }
                perform {
                    action.parameter.chosenBy = playerIndex
                    players[playerIndex].gold += action.parameter.gold
                    if (action.parameter.ingredients > 0) {
                        game.ingredients.deck.random(replayable, action.parameter.ingredients, "ingredients") { it.serialize() }.forEach {
                            it.moveTo(game.players[playerIndex].ingredients)
                        }
                    }
                    if (action.parameter.favors > 0) {
                        game.favors.deck.random(replayable, action.parameter.favors, "favors") { it.serialize() }.forEach { favor ->
                            if (favor.card == Favors.FavorType.HERBALIST) {
                                game.ingredients.deck.random(replayable, 3, "herbalist") { it.serialize() }.forEach {
                                    it.moveTo(game.players[playerIndex].ingredients)
                                }
                                game.queue.add(game.favors.herbalistDiscard as ActionDefinition<Model, Any>)
                            }
                            favor.moveTo(game.players[playerIndex].favors)
                        }
                    }
                }
            }
        }
        val turnPicker by component { TurnPicker(ctx) }

        inner class ActionSpace(ctx: Context, val name: String, val cost: Favors.FavorType? = null): Entity(ctx) {
            val spaces by component { mutableListOf<Int>() }
            val rows by component { mutableListOf<Pair<Int, MutableList<Int?>>?>() }
                // cubes in each row. Row 3 to [1, 2] means that player 3 has 1 cube, then 2 cubes in that row.
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
                    rows.add(0, playerIndex to cubes.toMutableList())
                    rows.removeAt(rows.indexOf(null))
                } else {
                    val rowIndex = rows.lastIndexOf(null)
                    rows[rowIndex] = playerIndex to cubes.toMutableList()
                }
            }

            private fun nextIndex(): Pair<Int, Int>? {
                for (index in 0..rows.maxOf { it?.second?.size ?: 0 }) {
                    for (rowIndex in rows.indices) {
                        val row = rows[rowIndex]
                        if (row != null && row.second.size > index && row.second[index] != null) {
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
                return row.first to row.second[actionListIndex]!!
            }

            fun resolveNext(playerIndex: Int): Int {
                val list = rows.first { it?.first == playerIndex }!!.second
                val value = list.first { it != null }!!
                list.remove(value)
                return value
            }
            fun has(playerIndex: Int): Boolean {
                val list = rows.find { it?.first == playerIndex }?.second ?: return false
                return list.any { it != null }
            }
            fun resolveNext(): Int {
                val index = nextIndex()!!
                val (rowIndex, actionListIndex) = index
                val row = rows[rowIndex]!!
                val count = row.second[actionListIndex]
                row.second[actionListIndex] = null
                return count!!
            }

            fun nextPlayerIndex(): Int? = this.next()?.first
        }

        inner class Player(val model: Model, ctx: Context, val playerIndex: Int): Entity(ctx) {
            var gold by component { 2 }
            var reputation by component { 10 }
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
                    model.ingredients.deck.random(replayable, startingIngredients, "startingIngredients-$playerIndex") { it.toString() }
                        .forEach { it.moveTo(value) }
                }
                .on(playerMixPotion) {
                    if (playerIndex == event.playerIndex) {
                        val discardIndices = if (artifacts.cards.contains(ArtifactActions.magicMortar))
                                listOf(replayable.int("discardIndex") { Random.Default.nextInt(0, 2) })
                            else listOf(0, 1)
                        discardIndices.forEach {
                            value.cards.remove(event.ingredients.toList()[it])
                        }
                    }
                }
                .privateView(playerIndex) { it.cards }
                .publicView { it.size }
        }

        val players by playerComponent { Player(this@Model, ctx, it) }
        val startingPlayer by playerReference { 0 }
            .setup { replayable.int("startingPlayer") { (0 until playerCount).random() } }
            .on(newRound) { value.next(players.size) }

        data class ActionChoice(val spot: HasAction, val count: Int, val associate: Boolean)
        data class ActionPlacement(val chosen: List<ActionChoice>): GameSerializable {
            override fun serialize(): Any = chosen.map { "${it.associate}/${it.spot.actionSpace.name}" }
        }
        fun nextPlayer(): Int? = turnPicker.options.last { it.chosenBy != null }.chosenBy
        val actionPlacement by actionSerializable<Model, ActionPlacement>("action", ActionPlacement::class) {
            precondition {
                playerIndex == nextPlayer()
            }
            requires { action.parameter.chosen.sumOf { it.count } <= players[playerIndex].actionCubesAvailable }
            requires { action.parameter.chosen.groupBy { it.spot }.values.all { it.count { c -> c.associate } <= 1 } }
            requires { players[playerIndex].favors.cards.count { it == Favors.FavorType.CUSTODIAN } >= action.parameter.chosen.count { it.spot == custodian } }
            requires { action.parameter.chosen.all { it.spot.actionAvailable(playerIndex, action.parameter.chosen) != false } }
            perform {
                for (i in 1..action.parameter.chosen.count { it.spot == custodian }) {
                    players[playerIndex].favors.card(Favors.FavorType.CUSTODIAN).remove()
                }
                action.parameter.chosen.groupBy { it.spot }.forEach {
                    it.key.actionSpace.place(playerIndex, it.value)
                }
                turnPicker.options.first { it.chosenBy == playerIndex }.chosenBy = null
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
        val debunkTheory by component { TheoryActions.DebunkTheory(this@Model, this.ctx) }
        val publishTheory by component { TheoryActions.PublishTheory(this@Model, this.ctx) }
        val testStudent by component { PotionActions.TestStudent(this@Model, ctx) }
        val testSelf by component { PotionActions.TestSelf(this@Model, ctx) }
        val exhibition by component { PotionActions.Exhibition(this@Model, this.ctx) }
        val theoryBoard by component { TheoryActions.TheoryBoard(this@Model, ctx) }
        val cancelledActions by value { mutableListOf<Int>() }
        fun cancelAction(space: HasAction) = action<Model, Unit>("cancel", Unit::class) {
            precondition { playerIndex == space.actionSpace.nextPlayerIndex() }
            perform {
                val count = space.actionSpace.resolveNext()
                for (i in 1..count) cancelledActions.add(playerIndex)
                favors.favorsPlayed.cards.clear()
            }
        }

        val actionSpaces = listOf<HasAction>(
            ingredients, transmute, custodian, sellPotion, buyArtifact,
            debunkTheory, publishTheory, testStudent, testSelf,
            exhibition
        )
    }

    suspend fun stateChecks(scope: GameFlowScope<Model>) {
        if (scope.game.queue.isNotEmpty()) {
            scope.step("empty queue") {
                enableAction(scope.game.queue[0])
            }.loopUntil { scope.game.queue.isEmpty() }
        }
    }

    val game = GamesApi.gameContext("Alchemists", Model::class) {
        val master = config("master") { false }
        players(2..4)
        init { Model(ctx, master) }
        gameFlow {
            println("SOLUTION: " + game.alchemySolution)
            game.gameInit.invoke(this, Unit)
            step("choose favors") {
                enableAction(game.favors.chooseFavor)
            }.loopUntil { game.favors.playersDiscardingSetupFavor.isEmpty() }
            stateChecks(this)

            for (round in 1..6) {
                game.newRound(this, round)
                game.sellPotion.reset()
                step("round $round - turnPicker") {
                    enableAction(game.turnPicker.action)
                }.loopUntil { game.players.indices.all { player -> game.turnPicker.options.any { it.chosenBy == player } } }

                stateChecks(this)

                step("round $round - placeActions") {
                    enableAction(game.actionPlacement)
                    enableAction(game.favors.assistant)
                }.loopUntil { game.turnPicker.options.all { it.chosenBy == null } }

                for (space in game.actionSpaces) {
                    step("resolve round $round ${space.actionSpace.name}") {
                        if (game.queue.isNotEmpty()) {
                            enableAction(game.queue.first())
                        } else {
                            enableAction(space.action)
                            enableAction(game.cancelAction(space))
                            space.extraActions().forEach { enableAction(it) }
                        }
                    }.loopUntil {
                        action?.parameter !is Favors.FavorType
                            && game.queue.isEmpty()
                            && space.actionSpace.rows.all { it == null || it.second.all { i -> i == null } }
                    }
                    game.spaceDone.invoke(this, space)
                }
            }
        }
    }

}
