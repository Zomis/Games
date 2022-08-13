package net.zomis.games.impl.ttt

import net.zomis.games.PlayerEliminationsWrite
import net.zomis.games.WinResult
import net.zomis.games.api.Games
import net.zomis.games.api.GamesApi
import net.zomis.games.api.components
import net.zomis.games.components.Point
import net.zomis.games.common.next
import net.zomis.games.components.grids.Transformation
import net.zomis.games.components.grids.mnkLines
import net.zomis.games.components.grids.subGrid
import net.zomis.games.components.grids.transform
import net.zomis.games.context.Context
import net.zomis.games.context.ContextHolder
import net.zomis.games.context.Entity
import net.zomis.games.dsl.GameSerializable
import net.zomis.games.impl.ttt.ultimate.TTPlayer

object Pentago {
    const val SIZE = 3

    data class PentagoConfig(val turnDifferent: Boolean, val turnBothDirections: Boolean)
    data class Turning(val topLeft: Point, val clockwise: Boolean): GameSerializable {
        override fun serialize(): Any = "${topLeft.serialize()}-$clockwise"
    }

    class Model(override val ctx: Context, val config: PentagoConfig): Entity(ctx), ContextHolder {
        var currentPlayer by component { 0 }
        val grid by component {
            Games.components.grid(2*SIZE, 2*SIZE) { _, _ -> TTPlayer.NONE }
        }.publicView { map -> map.view { it } }
        var lastPlacement: Point = Point(0, 0)

        val place = actionSerializable<Model, Point>("place", Point::class) {
            precondition { playerIndex == currentPlayer }
            requires { grid.get(action.parameter.x, action.parameter.y) == TTPlayer.NONE }
            options { grid.points() }
            perform {
                grid.set(action.parameter.x, action.parameter.y, TTPlayer.forIndex(currentPlayer))
                lastPlacement = action.parameter
            }
        }
        val turn = actionSerializable<Model, Turning>("turn", Turning::class) {
            precondition { playerIndex == currentPlayer }
            choose {
                options({
                    listOf(
                        Point(0, 0),
                        Point(SIZE, 0),
                        Point(0, SIZE),
                        Point(SIZE, SIZE)
                    ).filter {
                        val placementInside = it.topLeftOfRect(SIZE, SIZE)
                            .contains(lastPlacement.x, lastPlacement.y)
                        config.turnDifferent || placementInside
                    }
                }) {direction ->
                    options({ listOf(true, false).filter { config.turnBothDirections || it } }) { clockwise ->
                        parameter(Turning(direction, clockwise))
                    }
                }
            }
            perform {
                val apply = if (action.parameter.clockwise) Transformation.ROTATE_90_CLOCKWISE else Transformation.ROTATE_90_ANTI_CLOCKWISE
                grid.subGrid(action.parameter.topLeft.x, action.parameter.topLeft.y, SIZE, SIZE).transform(apply)
                winCheck(eliminations)
                currentPlayer = currentPlayer.next(2)
            }
        }

        private fun winCheck(eliminations: PlayerEliminationsWrite) {
            val lines = grid.mnkLines(true)
            val wins = lines.mapNotNull { line -> line.hasConsecutive(5) { it.takeIf { i -> i.isExactlyOnePlayer } } }.toSet()
            when (wins.size) {
                1 -> eliminations.singleWinner(wins.first().index())
                2 -> eliminations.eliminateRemaining(WinResult.DRAW)
            }
        }
    }

    val game = GamesApi.gameContext("Pentago", Model::class) {
        val turnDifferent = this.config("turnDifferent") { true }
        val turnBothDirections = this.config("turnBothDirections") { true }
        this.init {
            Model(ctx, PentagoConfig(turnDifferent = turnDifferent.value, turnBothDirections = turnBothDirections.value))
        }
        this.players(2..2)
        this.gameFlow {
            this.loop {
                this.step("Place") {
                    this.enableAction(game.place)
                }
                this.step("Turn") {
                    this.enableAction(game.turn)
                }
            }
        }
    }

}