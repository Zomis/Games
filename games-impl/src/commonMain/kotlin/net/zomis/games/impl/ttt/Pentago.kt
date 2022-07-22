package net.zomis.games.impl.ttt

import net.zomis.games.PlayerEliminationsWrite
import net.zomis.games.WinResult
import net.zomis.games.api.Games
import net.zomis.games.api.GamesApi
import net.zomis.games.api.components
import net.zomis.games.common.Direction8
import net.zomis.games.common.Point
import net.zomis.games.common.next
import net.zomis.games.components.*
import net.zomis.games.context.Context
import net.zomis.games.context.ContextHolder
import net.zomis.games.context.Entity
import net.zomis.games.dsl.GameSerializable
import net.zomis.games.impl.ttt.ultimate.TTPlayer

object Pentago {
    const val SIZE = 3

    data class Turning(val direction: Direction8, val clockwise: Boolean): GameSerializable {
        override fun serialize(): Any = "${direction.serialize()}-$clockwise"
    }

    class Model(override val ctx: Context): Entity(ctx), ContextHolder {
        var currentPlayer by component { 0 }
        val grid by component {
            Games.components.grid(2*SIZE, 2*SIZE) { _, _ -> TTPlayer.NONE }
        }.publicView { map -> map.view { it } }

        val place = actionSerializable<Model, Point>("place", Point::class) {
            precondition { playerIndex == currentPlayer }
            requires { grid.get(action.parameter.x, action.parameter.y) == TTPlayer.NONE }
            options { grid.points() }
            perform {
                grid.set(action.parameter.x, action.parameter.y, TTPlayer.forIndex(currentPlayer))
            }
        }
        val turn = actionSerializable<Model, Turning>("turn", Turning::class) {
            precondition { playerIndex == currentPlayer }
            choose {
                options({ Direction8.diagonals() }) {direction ->
                    options({ listOf(true, false) }) { clockwise ->
                        parameter(Turning(direction, clockwise))
                    }
                }
            }
            perform {
                val apply = if (action.parameter.clockwise) Transformation.ROTATE_90_CLOCKWISE else Transformation.ROTATE_90_ANTI_CLOCKWISE
                val point = when (action.parameter.direction) {
                    Direction8.NE -> Point(1, 0)
                    Direction8.NW -> Point(0, 0)
                    Direction8.SW -> Point(0, 1)
                    Direction8.SE -> Point(1, 1)
                    else -> throw IllegalArgumentException("Invalid direction: ${action.parameter.direction}")
                }.times(SIZE)
                grid.subGrid(point.x, point.y, SIZE, SIZE).transform(apply)
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
        this.init { Model(ctx) }
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