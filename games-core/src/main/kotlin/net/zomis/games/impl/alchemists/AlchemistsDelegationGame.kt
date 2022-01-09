package net.zomis.games.impl.alchemists

import net.zomis.games.api.GamesApi
import net.zomis.games.common.PlayerIndex
import net.zomis.games.common.Players
import net.zomis.games.common.next
import net.zomis.games.common.times
import net.zomis.games.context.Context
import net.zomis.games.context.ContextHolder
import net.zomis.games.context.Entity
import net.zomis.games.dsl.GameSerializable
import net.zomis.games.dsl.flow.ActionDefinition

object AlchemistsDelegationGame {
    enum class Ingredient(private val char: Char) {
        PURPLE_MUSHROOM('A'), GREEN_PLANT('B'), BROWN_FROG('C'), YELLOW_CHICKEN_LEG('D'),
        BLUE_FLOWER('E'), GRAY_TREE('F'), RED_SCORPION('G'), BLACK_FEATHER('H'),
        ;
        override fun toString(): String = char.toString()
    }

    interface HasAction {
        val actionSpace: Model.ActionSpace
        val action: ActionDefinition<Model, *>
    }
    class Model(override val ctx: Context) : Entity(ctx), ContextHolder {
        val newRound by event(Int::class)
        val solution by component { emptyList<Ingredient>() }
            .setup {
                val list = Ingredient.values().toList()
                replayable.randomFromList("solution", list.shuffled(), list.size, Ingredient::toString)
            }
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
                    players[playerIndex].gold += -action.parameter.gold
                    if (action.parameter.ingredients > 0) {
                        game.ingredients.deck.random(replayable, action.parameter.ingredients, "ingredients") { it.toString() }.forEach {
                            it.moveTo(game.players[playerIndex].ingredients)
                        }
                    }
                }
            }
        }
        val turnPicker by component { TurnPicker(ctx) }

        inner class ActionSpace(ctx: Context, val name: String): Entity(ctx) {
            val spaces by component { mutableListOf<Int>() }
            val rows by component { mutableListOf<Pair<Int, MutableList<Int?>>?>() }
                // cubes in each row. Row 3 to [1, 2] means that player 3 has 1 cube, then 2 cubes in that row.
                .on(newRound) { value.indices.forEach { value[it] = null } }

            fun initialize(count: List<Int>, playerCount: Int): ActionSpace {
                spaces.addAll(count)
                rows.addAll((0 until playerCount).map { null })
                return this
            }

            fun nextCost(chosen: List<Pair<HasAction, Int>>): Int? {
                val spacesLeft = chosen.filter { it.first.actionSpace == this }.map { it.second }.fold(spaces) { acc, next -> acc.minusElement(next).toMutableList() }
                return spacesLeft.firstOrNull()
            }

            fun place(playerIndex: Int, cubes: List<Int?>) {
                val spacesLeft = this.spaces.toMutableList()
                check(spacesLeft.take(cubes.size) == cubes)
                val rowIndex = rows.lastIndexOf(null)
                rows[rowIndex] = playerIndex to cubes.toMutableList()
            }

            private fun nextIndex(): Pair<Int, Int>? {
                for (index in 0..rows.maxOf { it?.second?.maxOfOrNull { i -> i ?: 0 } ?: 0 }) {
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

            fun resolveNext() {
                val index = nextIndex() ?: return
                val (rowIndex, actionListIndex) = index
                val row = rows[rowIndex]!!
                row.second[actionListIndex] = null
            }

            fun nextPlayerIndex(): Int? = this.next()?.first
        }

        inner class Player(ctx: Context, val playerIndex: Int): Entity(ctx) {
            var gold by component { 2 }
            var reputation by component { 10 }
            val ingredients by cards<Ingredient>()
                .on(newRound) {
                    if (event == 1) this@Model.ingredients.deck.random(replayable, 3, "startingIngredients-$playerIndex") { it.toString() }
                        .forEach { it.moveTo(value) }
                }
                .privateView(playerIndex) { it.cards }
                .publicView { it.size }
        }

        val players by playerComponent { Player(ctx, it) }
        val startingPlayer by playerReference { 0 }
            .setup { replayable.int("startingPlayer") { (0 until playerCount).random() } }
            .on(newRound) { value.next(players.size) }

        inner class Ingredients(ctx: Context) : Entity(ctx), HasAction {
            val deck by cards<Ingredient>()
                .setup { it.cards.addAll(Ingredient.values().toList().times(5)); it }
                .publicView { it.size }
            val slots by cards<Ingredient>()
                .on(newRound) {
                    println("Refilling ingredients for round $event")
                    value.cards.clear()
                    deck.random(replayable, 5, "ingredients") { it.toString() }.forEach { it.moveTo(value) }
                }
            override val actionSpace by component { ActionSpace(this.ctx, "Ingredients") }
                .setup { it.initialize(if (playerCount == 4) listOf(1, 1) else listOf(1, 1, 1), playerCount) }
            override val action by action<Model, String>("takeIngredient", String::class) {
                precondition { playerIndex == actionSpace.nextPlayerIndex() }
                options { listOf("") + slots.cards.distinct().map { it.toString() } }
                perform {
                    actionSpace.resolveNext()
                    if (action.parameter.isEmpty()) {
                        deck.random(replayable, 1, "ingredientDeck") { it.toString() }
                            .forEach { it.moveTo(players[playerIndex].ingredients) }
                    } else {
                        slots.card(slots.cards.first { it.toString() == action.parameter }).moveTo(players[playerIndex].ingredients)
                    }
                }
            }
        }
        val ingredients by component { Ingredients(ctx) }
        data class ActionPlacement(val chosen: List<Pair<HasAction, Int>>)
        val actionPlacement by action<Model, ActionPlacement>("action", ActionPlacement::class) {
            precondition {
                playerIndex == turnPicker.options.last { it.chosenBy != null }.chosenBy
            }
            perform {
                action.parameter.chosen.groupBy { it.first }.mapValues { it.value.map { p -> p.second } }.forEach {
                    it.key.actionSpace.place(playerIndex, it.value)
                }
                turnPicker.options.first { it.chosenBy == playerIndex }.chosenBy = null
            }
            choose {
                recursive(emptyList<Pair<HasAction, Int>>()) {
                    parameter { ActionPlacement(chosen) }
                    until { this.chosen.size == actionCubeCount }
                    optionsWithIds({ actionSpaces.map {
                        it.actionSpace.name to (it to it.actionSpace.nextCost(chosen))
                    }.filter { it.second.second != null } }) { next ->
                        recursion(next) { acc, n -> acc + (n.first to n.second!!) }
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

        inner class Transmute(ctx: Context): Entity(ctx), HasAction {
            override val actionSpace by component { ActionSpace(this.ctx, "Transmute") }.setup { it.initialize(listOf(1, 2), playerCount) }
            override val action by action<Model, Ingredient>("transmute", Ingredient::class) {
                precondition { playerIndex == actionSpace.nextPlayerIndex() }
                options { game.players[playerIndex].ingredients.cards.distinct() }
                perform {
                    actionSpace.resolveNext()
                    game.players[playerIndex].ingredients.cards.remove(action.parameter)
                    game.players[playerIndex].gold++
                }
            }
        }
        val transmute by component { Transmute(ctx) }
        val actionSpaces = listOf<HasAction>(ingredients, transmute)

    }

    val game = GamesApi.gameContext("Alchemists", Model::class) {
        players(2..4)
        init { Model(ctx) }
        gameFlow {
            for (round in 1..6) {
                game.newRound(this, round)
                step("round $round - turnPicker") {
                    enableAction(game.turnPicker.action)
                }.loopUntil { game.players.indices.all { player -> game.turnPicker.options.any { it.chosenBy == player } } }

                step("round $round - placeActions") {
                    enableAction(game.actionPlacement)
                }.loopUntil { game.turnPicker.options.all { it.chosenBy == null } }

                for (space in game.actionSpaces) {
                    step("resolve round $round ${space.actionSpace.name}") {
                        enableAction(space.action)
                    }.loopUntil {
                        space.actionSpace.rows.all { it == null || it.second.all { i -> i == null } }
                    }
                }
            }
        }
    }

}
