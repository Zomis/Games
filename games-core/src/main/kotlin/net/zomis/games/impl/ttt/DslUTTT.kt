package net.zomis.games.impl.ttt

import net.zomis.games.WinResult
import net.zomis.games.common.Point
import net.zomis.games.dsl.*
import net.zomis.games.impl.ttt.ultimate.*

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
    val playAction = factory.action("play", TTBase::class).serialization({ Point(it.globalX, it.globalY) }, {
        game.game.getSmallestTile(it.x, it.y)!!
    })

    private fun GameDsl<TTController>.ttConfigs(options: TTOptions): List<GameConfig<Int>> {
        val m = config("m") { options.m }
        val n = config("n") { options.n }
        val k = config("k") { options.k }
        return listOf(m, n, k)
    }
    private fun GameDsl<TTController>.ttGame() {
        actionRules {
            ttRules()
            ttView()
        }
    }

    val game = factory.game("DSL-TTT") {
        val (m, n, k) = ttConfigs(TTOptions(3, 3, 3))
        setup {
            playersFixed(2)
            init {
                TTClassicController(TTFactories().classicMNK(config(m), config(n), config(k)))
            }
        }
        ttGame()
    }

    val gameConnect4 = factory.game("DSL-Connect4") {
        val (m, n, k) = ttConfigs(TTOptions(7, 6, 4))
        setup {
            playersFixed(2)
            init {
                TTClassicControllerWithGravity(TTFactories().classicMNK(config(m), config(n), config(k)))
            }
        }
        ttGame()
    }

    val gameUTTT = factory.game("DSL-UTTT") {
        val (m, n, k) = ttConfigs(TTOptions(3, 3, 3))
        setup {
            playersFixed(2)
            init {
                TTUltimateController(TTFactories().ultimateMNK(config(m), config(n), config(k)))
            }
        }
        actionRules {
            ttRules()
            view("currentPlayer") { game.currentPlayer.index() }
            view("boards") {
                val actions = action(playAction).options().associateWith { true }
                game.game.subs().chunked(3).map {areas ->
                    areas.map {area ->
                        val chunkedSubs = area.subs().chunked(3).map {tiles ->
                            tiles.map { tile ->
                                mapOf(
                                    "owner" to tile.wonBy.index().takeIf { i -> i >= 0 },
                                    "actionable" to actions.containsKey(tile)
                                )
                            }
                        }
                        mapOf("owner" to area.wonBy.index().takeIf { i -> i >= 0 },
                            "subs" to chunkedSubs)
                    }
                }
            }
            view("activeBoard") {
                val active = (game as TTUltimateController).activeBoard ?: return@view null
                mapOf("x" to active.x, "y" to active.y)
            }
        }
    }

    val gameReversi = factory.game("DSL-Reversi") {
        setup {
            playersFixed(2)
            init { TTOthello(8) }
        }
        ttGame()
    }

    private fun GameActionRules<TTController>.ttView() {
        view("currentPlayer") { game.currentPlayer.index() }
        view("board") {
            game.game.view {
                mapOf("owner" to it.wonBy.index().takeIf { n -> n >= 0 })
            }
        }
    }

    private fun GameActionRules<TTController>.ttRules() {
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
        view("actionName") { playAction.name }
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
