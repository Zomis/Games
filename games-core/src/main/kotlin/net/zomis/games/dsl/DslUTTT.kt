package net.zomis.games.dsl

import net.zomis.tttultimate.TTBase
import net.zomis.tttultimate.TTFactories
import net.zomis.tttultimate.TTPlayer
import net.zomis.tttultimate.games.*

data class TTOptions(val m: Int, val n: Int, val k: Int)
fun TTPlayer.index(): Int {
    return when (this) {
        TTPlayer.X -> 0
        TTPlayer.O -> 1
        TTPlayer.NONE -> -1
        TTPlayer.XO -> -1
        TTPlayer.BLOCKED -> -1
    }
}

class DslTTT {
    val playAction = createActionType("play", Point::class)
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
        logic(ttLogic(grid))
        view(ttView(grid))
    }

    val gameConnect4 = createGame<TTController>("Connect4") {
        val grid = gridSpec<TTBase> {
            size(model.game.sizeX, model.game.sizeY)
            getter { x, y -> model.game.getSub(x, y)!! }
        }
        setup(TTOptions::class) {
            defaultConfig {
                TTOptions(7, 6, 4)
            }
            init {conf ->
                TTClassicControllerWithGravity(TTFactories().classicMNK(conf!!.m, conf.n, conf.k))
            }
        }
        logic(ttLogic(grid))
        view(ttView(grid))
    }

    val gameUTTT = createGame<TTController>("UTTT") {
        val grid = gridSpec<TTBase> {
            size(model.game.sizeX * model.game.getSub(0, 0)!!.sizeX,
                model.game.sizeY * model.game.getSub(0, 0)!!.sizeY)
            getter { x, y -> model.game.getSmallestTile(x, y)!! }
        }
        setup(TTOptions::class) {
            defaultConfig {
                TTOptions(3, 3, 3)
            }
            init {conf ->
                TTUltimateController(TTFactories().ultimateMNK(conf!!.m, conf.n, conf.k))
            }
        }
        logic(ttLogic(grid))
        view(ttView(grid)) // TODO: Needs view improvements. (Logic can still work, perhaps...
    }

    val gameReversi = createGame<TTController>("Reversi") {
        val grid = gridSpec<TTBase> {
            size(model.game.sizeX, model.game.sizeY)
            getter { x, y -> model.game.getSmallestTile(x, y)!! }
        }
        setup(Unit::class) {
            defaultConfig { Unit }
            init { TTOthello(8) }
        }
        logic(ttLogic(grid))
        view(ttView(grid))
    }

    private val winner: (TTController) -> Int? = {
        when {
            it.isGameOver -> it.wonBy.index()
            isPlacesLeft(it.game) -> null
            else -> -1 // TODO: Make a better way to represent 'draw'
        }
    }

    private fun ttView(grid: GridDsl<TTController, TTBase>): GameViewDsl<TTController> = {
        currentPlayer { it.currentPlayer.index() }
        winner(winner)
        grid("board", grid) {
            owner { it.wonBy.index().takeIf {n -> n >= 0 } }
        }
    }

    private fun ttLogic(grid: GridDsl<TTController, TTBase>): GameLogicDsl<TTController> = {
        winner(winner)
        action2D(playAction, grid) {
            allowed { it.playerIndex == it.game.currentPlayer.index() && it.game.isAllowedPlay(it.target) }
            effect {
                it.game.play(it.target)
            }
        }
    }

    private fun isPlacesLeft(tt: TTBase): Boolean {
        if (!tt.hasSubs()) {
            return !tt.isWon
        }
        return !tt.isWon && (0 until tt.sizeY).asSequence().flatMap { y ->
            (0 until tt.sizeX).asSequence().map { x ->
                tt.getSub(x, y)!!
            }
        }.any { isPlacesLeft(it) }
    }

}
