package net.zomis.games.impl.ttt

import net.zomis.games.WinResult
import net.zomis.games.common.Point
import net.zomis.games.dsl.GameCreator
import net.zomis.games.dsl.GameRulesDsl
import net.zomis.games.dsl.GameViewDsl
import net.zomis.games.dsl.GridDsl
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

object DslTTT {
    val factory = GameCreator(TTController::class)
    val playAction = factory.action("play", TTBase::class).serialization(Point::class, { Point(it.globalX, it.globalY) }, {
        game.game.getSmallestTile(it.x, it.y)!!
    })
    val game = factory.game("DSL-TTT") {
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
        rules(ttRules())
        view(ttView(grid))
    }

    val gameConnect4 = factory.game("DSL-Connect4") {
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
        rules(ttRules())
        view(ttView(grid))
    }

    val gameUTTT = factory.game("DSL-UTTT") {
        setup(TTOptions::class) {
            defaultConfig {
                TTOptions(3, 3, 3)
            }
            init {conf ->
                TTUltimateController(TTFactories().ultimateMNK(conf!!.m, conf.n, conf.k))
            }
        }
        rules(ttRules())
        view {
            currentPlayer { it.currentPlayer.index() }
            value("boards") {e ->
                e.game.subs().chunked(3).map {areas ->
                    areas.map {area ->
                        val chunkedSubs = area.subs().chunked(3).map {tiles ->
                            tiles.map { tile ->
                                mapOf("owner" to tile.wonBy.index().takeIf { i -> i >= 0 })
                            }
                        }
                        mapOf("owner" to area.wonBy.index().takeIf { i -> i >= 0 },
                            "subs" to chunkedSubs)
                    }
                }
            }
            value("activeBoard") {
                val active = (it as TTUltimateController).activeBoard ?: return@value null
                mapOf("x" to active.x, "y" to active.y)
            }
        }
    }

    val gameReversi = factory.game("DSL-Reversi") {
        val grid = gridSpec<TTBase> {
            size(model.game.sizeX, model.game.sizeY)
            getter { x, y -> model.game.getSmallestTile(x, y)!! }
        }
        setup(Unit::class) {
            defaultConfig { Unit }
            init { TTOthello(8) }
        }
        rules(ttRules())
        view(ttView(grid))
    }

    private fun ttView(grid: GridDsl<TTController, TTBase>): GameViewDsl<TTController> = {
        currentPlayer { it.currentPlayer.index() }
        grid("board", grid) {
            owner { it.wonBy.index().takeIf {n -> n >= 0 } }
        }
    }

    private fun ttRules(): GameRulesDsl<TTController> = {
        allActions.precondition { playerIndex == game.currentPlayer.index() }
        action(playAction) {
            options { allSmallest(game.game).asIterable() }
            requires { game.isAllowedPlay(action.parameter) }
            effect { game.play(action.parameter) }
        }
        allActions.after {
            if (game.isGameOver && game.wonBy.isExactlyOnePlayer) eliminations.singleWinner(game.wonBy.index())
            else if (game.isGameOver && game.wonBy == TTPlayer.BLOCKED) eliminations.eliminateRemaining(WinResult.DRAW)
            else if (!isPlacesLeft(game.game)) eliminations.eliminateRemaining(WinResult.DRAW)
        }
    }

    private fun allSmallest(base: TTBase): Sequence<TTBase> {
        return sequence {
            if (!base.hasSubs()) {
                yield(base)
            } else {
                (0 until base.sizeY).forEach { y ->
                    (0 until base.sizeX).forEach { x ->
                        yieldAll(allSmallest(base.getSub(x, y)!!))
                    }
                }
            }
        }
    }

    private fun isPlacesLeft(tt: TTBase) = allSmallest(tt).any { !it.isWon }

}
