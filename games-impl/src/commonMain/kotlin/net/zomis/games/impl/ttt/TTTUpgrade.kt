package net.zomis.games.impl.ttt

import net.zomis.games.WinResult
import net.zomis.games.api.Games
import net.zomis.games.api.GamesApi
import net.zomis.games.api.components
import net.zomis.games.common.Point
import net.zomis.games.components.mnkLines
import net.zomis.games.impl.ttt.ultimate.TTPlayer

object TTTUpgrade {

    class Tile(val x: Int, val y: Int, var player: TTPlayer, var level: Int)
    class Model {
        val startingLevels = (1..3).flatMap { listOf(it, it) }

        var currentPlayer = TTPlayer.X
        val board = Games.components.grid(3, 3) { x, y -> Tile(x, y, TTPlayer.NONE, 0) }
        val players = listOf(startingLevels, startingLevels).map { it.toMutableList() }
    }
    data class Action(val position: Point, val level: Int)

    val factory = GamesApi.gameCreator(Model::class)
    val play = factory.action("play", Action::class)
    val game = factory.game("TTTUpgrade") {
        setup {
            playersFixed(2)
            init { Model() }
        }
        gameFlow {
            loop {
                step("player ${game.currentPlayer}") {
                    yieldAction(play) {
                        precondition { game.currentPlayer.index() == playerIndex }
                        choose {
                            options({ game.players[playerIndex].distinct() }) { level ->
                                options({ game.board.all().map { Point(it.x, it.y) } }) { point ->
                                    parameter(Action(point, level))
                                }
                            }
                        }
                        requires { game.players[game.currentPlayer.index()].contains(action.parameter.level) }
                        requires { action.parameter.level > game.board.point(action.parameter.position).value.level }
                        perform {
                            game.players[game.currentPlayer.index()].remove(action.parameter.level)
                            val tile = game.board.point(action.parameter.position).value
                            tile.player = game.currentPlayer
                            tile.level = action.parameter.level
                            game.currentPlayer = game.currentPlayer.next()
                        }
                    }
                }
            }
        }
        gameFlowRules {
            afterActionRule("win condition check") {
                applyForEach {
                    val mnkLines = game.board.mnkLines(true)
                    mnkLines.filter { line -> line.hasConsecutive(3) { it.player }?.isExactlyOnePlayer ?: false }
                        .asIterable()
//                    mnkLines.filter { winLine ->
//                        winLine.items.map { it.player }.distinct().let { it.size == 1 && it.first().isExactlyOnePlayer }
//                    }.asIterable()
                }.effect {
                    val winner = it.items.first().player.index()
                    eliminations.singleWinner(winner)
                }
            }
            afterActionRule("draw check") {
                appliesWhen { game.players.all { it.isEmpty() } }
                effect {
                    eliminations.eliminateRemaining(WinResult.DRAW)
                }
            }
            beforeReturnRule("view") {
                view("board") {
                    game.board.view { tile ->
                        mapOf("player" to tile.player.name, "level" to tile.level)
                    }
                }
                view("chosen") { this.actionsChosen().chosen()?.chosen?.firstOrNull() }
                view("currentPlayer") { game.currentPlayer.index() }
                view("players") {
                    game.players
                }
            }
        }
    }

}