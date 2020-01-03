package net.zomis.games.dsl

import net.zomis.games.impl.TTT3D
import net.zomis.games.impl.TTT3DPoint

class DslTTT3D {
    val playAction = createActionType("play", Point::class)
    val SIZE = 4
    val game = createGame<TTT3D>("TTT3D") {
        val grid = gridSpec<Array<TTT3DPoint>> {
            size(SIZE, SIZE)
            getter { x, y -> model.pieces[y][x] }
        }
        setup(Unit::class) {
            defaultConfig { Unit }
            init { TTT3D() }
        }
        logic(ttLogic(grid))
        view(ttView(grid))
    }

    private val winner: (TTT3D) -> Int? = {
        when {
            it.isDraw() -> -1 // TODO: Make a better way to represent 'draw'
            else -> it.findWinner()?.playerIndex
        }
    }

    private fun ttView(grid: GridDsl<TTT3D, Array<TTT3DPoint>>): GameViewDsl<TTT3D> = {
        currentPlayer { it.currentPlayer.playerIndex }
        winner(winner)
        grid("board", grid) {
            property("row") { it.map { piece -> piece.piece?.playerIndex } }
        }
    }

    private fun ttLogic(grid: GridDsl<TTT3D, Array<TTT3DPoint>>): GameLogicDsl<TTT3D> = {
        winner(winner)
        action2D(playAction, grid) {
            allowed { it.playerIndex == it.game.currentPlayer.playerIndex && it.game.canPlayAt(it.y, it.x) }
            effect {
                it.game.playAt(it.y, it.x)
            }
        }
    }

}
