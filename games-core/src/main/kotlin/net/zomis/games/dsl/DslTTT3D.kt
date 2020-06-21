package net.zomis.games.dsl

import net.zomis.games.WinResult
import net.zomis.games.common.Grid2D
import net.zomis.games.common.Point
import net.zomis.games.impl.TTT3D
import net.zomis.games.impl.TTT3DPoint

class DslTTT3D {
    val factory = GameCreator(TTT3D::class)
    val playAction = factory.action("play", Point::class)
    val SIZE = 4
    val game = factory.game("TTT3D") {
        val grid = gridSpec<Array<TTT3DPoint>> {
            size(SIZE, SIZE)
            getter { x, y -> model.pieces[y][x] }
        }
        setup(Unit::class) {
            defaultConfig { Unit }
            init { TTT3D() }
        }
        rules {
            allActions.precondition { playerIndex == game.currentPlayer.playerIndex }
            action(playAction) {
                options { Grid2D(SIZE, SIZE).points().toList() }
                requires { game.canPlayAt(action.parameter.y, action.parameter.x) }
                effect { game.playAt(action.parameter.y, action.parameter.x) }
            }
            allActions.after {
                game.findWinner()?.let { it.playerIndex }?.let { eliminations.singleWinner(it) }
                if (game.isDraw()) eliminations.eliminateRemaining(WinResult.DRAW)
            }
        }
        view {
            currentPlayer { it.currentPlayer.playerIndex }
            grid("board", grid) {
                property("row") { it.map { piece -> piece.piece?.playerIndex } }
            }
        }
    }

}
