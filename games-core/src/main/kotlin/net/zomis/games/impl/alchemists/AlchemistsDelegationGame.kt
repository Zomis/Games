package net.zomis.games.impl.alchemists

import net.zomis.games.api.GamesApi
import net.zomis.games.common.PlayerIndex
import net.zomis.games.common.Players
import net.zomis.games.common.next
import net.zomis.games.context.Context
import net.zomis.games.context.ContextHolder
import net.zomis.games.context.Entity
import net.zomis.games.dsl.GameSerializable
import net.zomis.games.dsl.flow.ActionDefinition

object AlchemistsDelegationGame {
    interface HasAction {
        val actionSpace: Model.ActionSpace
        val action: ActionDefinition<Model, *>
        fun actionAvailable(): Boolean = true
    }
    class Model(override val ctx: Context) : Entity(ctx), ContextHolder {
        val stack by component { mutableListOf<ActionDefinition<Model, Any>>() }
        val newRound by event(Int::class)
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
            var hospital by component { 0 }
            var artifacts by cards<ArtifactActions.Artifact>()
            val actionCubesAvailable by dynamicValue { this@Model.actionCubeCount - hospital }
            val ingredients by cards<Ingredient>()
                .on(newRound) {
                    if (event == 1) this@Model.ingredients.deck.random(replayable, 3, "startingIngredients-$playerIndex") { it.toString() }
                        .forEach { it.moveTo(value) }
                }
                .on(playerMixPotion) {
                    if (playerIndex == event.playerIndex) {
                        value.cards.remove(event.ingredients.first)
                        value.cards.remove(event.ingredients.second)
                    }
                }
                .privateView(playerIndex) { it.cards }
                .publicView { it.size }
        }

        val players by playerComponent { Player(ctx, it) }
        val startingPlayer by playerReference { 0 }
            .setup { replayable.int("startingPlayer") { (0 until playerCount).random() } }
            .on(newRound) { value.next(players.size) }

        data class ActionPlacement(val chosen: List<Pair<HasAction, Int>>): GameSerializable {
            override fun serialize(): Any = chosen.map { it.first.actionSpace.name }
        }
        val actionPlacement by actionSerializable<Model, ActionPlacement>("action", ActionPlacement::class) {
            precondition {
                playerIndex == turnPicker.options.last { it.chosenBy != null }.chosenBy
            }
            requires { action.parameter.chosen.sumOf { it.second } <= players[playerIndex].actionCubesAvailable }
            perform {
                action.parameter.chosen.groupBy { it.first }.mapValues { it.value.map { p -> p.second } }.forEach {
                    it.key.actionSpace.place(playerIndex, it.value)
                }
                turnPicker.options.first { it.chosenBy == playerIndex }.chosenBy = null
            }
            choose {
                recursive(emptyList<Pair<HasAction, Int>>()) {
                    parameter { ActionPlacement(chosen) }
                    until { this.chosen.sumOf { it.second } == players[playerIndex].actionCubesAvailable }
                    optionsWithIds({ actionSpaces.map {
                        it.actionSpace.name to (it to it.actionSpace.nextCost(chosen))
                    }.filter {
                        it.second.second != null && it.second.second!! <= players[playerIndex].actionCubesAvailable - chosen.sumOf { c -> c.second }
                    }}) { next ->
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

        val favors by component { Favors.FavorDeck(this@Model, ctx) }
        val ingredients by component { IngredientActions.Ingredients(this@Model, ctx) }
        val transmute by component { IngredientActions.Transmute(this@Model, ctx) }
        val sellPotion by component { SellAction.SellHero(this@Model, ctx) }
        val buyArtifact by component { ArtifactActions.BuyArtifact(this@Model, ctx) }
        val debunkTheory by component { TheoryActions.DebunkTheory(this@Model, this.ctx) }
        val publishTheory by component { TheoryActions.PublishTheory(this@Model, this.ctx) }
        val testStudent by component { PotionActions.TestStudent(this@Model, ctx) }
        val testSelf by component { PotionActions.TestSelf(this@Model, ctx) }
        val exhibition by component { PotionActions.Exhibition(this@Model, this.ctx) }

        val actionSpaces = listOf<HasAction>(
            ingredients, transmute, sellPotion, buyArtifact,
            debunkTheory, publishTheory, testStudent, testSelf,
            exhibition
        )

    }

    val game = GamesApi.gameContext("Alchemists", Model::class) {
        players(2..4)
        init { Model(ctx) }
        gameFlow {
            println("SOLUTION: " + game.alchemySolution)
            for (round in 1..6) {
                game.newRound(this, round)
                game.sellPotion.reset()
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
