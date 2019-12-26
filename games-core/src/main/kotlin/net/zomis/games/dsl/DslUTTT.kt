package net.zomis.games.dsl

import net.zomis.tttultimate.TTBase
import net.zomis.tttultimate.TTFactories
import net.zomis.tttultimate.TTPlayer
import net.zomis.tttultimate.games.TTClassicController
import net.zomis.tttultimate.games.TTController

data class TTOptions(val m: Int, val n: Int, val k: Int)
fun TTPlayer.index(): Int {
    return when (this) {
        TTPlayer.X -> 0
        TTPlayer.O -> 1
        TTPlayer.NONE -> -1
        TTPlayer.XO -> -1
    }
}

class DslTTT {
    val playAction = createActionType<Point>("play", Point::class)
    val game = createGame<TTController>("TTT") {
        val grid = gridSpec<TTBase> {
            size(model.game.sizeX, model.game.sizeY)
            getter { x, y -> model.game.getSub(x, y)!! }
        }
        setup(TTOptions::class) {
            defaultConfig {
                TTOptions(3, 3, 3)
            }
            init {conf ->
                TTClassicController(TTFactories().classicMNK(conf!!.m, conf.n, conf.k))
            }
        }
        logic {
            action2D(playAction, grid) {
                allowed { it.playerIndex == it.game.currentPlayer.index() && it.game.isAllowedPlay(it.target) }
                effect {
                    it.game.play(it.target)
                }
            }
        }
        view {
            currentPlayer { it.currentPlayer.index() }
            winner { if (it.isGameOver) it.wonBy.index() else null }
            grid("board", grid) {
                owner { it.wonBy.index().takeIf {n -> n >= 0 } }
            }
        }
    }

}
