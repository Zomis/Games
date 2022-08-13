package net.zomis.games.impl.ttt

import net.zomis.games.WinResult
import net.zomis.games.components.Grid2D
import net.zomis.games.components.Point
import net.zomis.games.dsl.GameCreator

object TTT3DGame {
    val factory = GameCreator(TTT3D::class)
    val playAction = factory.action("play", Point::class)
    val SIZE = 4
    val game = factory.game("DSL-TTT3D") {
        setup(Unit::class) {
            defaultConfig { Unit }
            init { TTT3D() }
        }
        actionRules {
            allActions.precondition { playerIndex == game.currentPlayer.playerIndex }
            action(playAction) {
                options { Grid2D(SIZE, SIZE).points().toList() }
                requires { game.canPlayAt(action.parameter.y, action.parameter.x) }
                effect { game.playAt(action.parameter.y, action.parameter.x) }
            }
            allActions.after {
                game.findWinner()?.playerIndex?.also { eliminations.singleWinner(it) }
                if (game.isDraw()) eliminations.eliminateRemaining(WinResult.DRAW)
            }
            view("currentPlayer") { game.currentPlayer.playerIndex }
            view("board") {
                (0 until SIZE).map { y ->
                    (0 until SIZE).map { x ->
                        mapOf("row" to game.pieces[y][x].map { it.piece?.playerIndex })
                    }
                }
            }
        }
    }

}
