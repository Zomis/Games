package net.zomis.games.server2.games.impl

import klog.KLoggers
import kotlinx.coroutines.runBlocking
import net.zomis.bestBy
import net.zomis.common.pmap
import net.zomis.games.ais.AlphaBeta
import net.zomis.games.dsl.Point
import net.zomis.tttultimate.TTFactories
import net.zomis.tttultimate.TTPlayer
import net.zomis.tttultimate.TTWinCondition
import net.zomis.tttultimate.Winnable
import net.zomis.tttultimate.games.*

class TTAlphaBeta(val level: Int) {

    private val logger = KLoggers.logger(this)

    private val factories = TTFactories()
    private fun copy(game: TTController): TTController {
        val history = game.saveHistory()
        val copied = when (game) {
            is TTOthello -> TTOthello()
            is TTUltimateController -> TTUltimateController(factories.ultimateMNK(3, 3, 3))
            is TTClassicControllerWithGravity -> TTClassicControllerWithGravity(factories.classicMNK(7, 6, 4))
            is TTClassicController -> TTClassicController(factories.classicMNK(3, 3, 3))
            else -> throw IllegalArgumentException("$this is not able to copy $game")
        }
        try {
            copied.makeMoves(history)
        } catch (e: Exception) {
            logger.error(e, "Unable to repeat moves: $history in $copied")
            throw e
        }
        return copied
    }

    private val actions: (TTController) -> List<Point> = {model ->
        var subs = model.game.subs()
        if (model is TTUltimateController) {
            subs = subs.flatMap { it.subs() }
        }
        subs.filter { model.isAllowedPlay(it) }.map { Point(it.globalX, it.globalY) }
    }
    private val branching: (TTController, Point) -> TTController = { game, move ->
        val copy = this.copy(game)
        copy.play(copy.game.getSmallestTile(move.x, move.y))
        copy
    }
    private val terminalState: (TTController) -> Boolean = { it.isGameOver }

    private fun TTWinCondition.emptySpaces(): Int {
        return this.hasCurrently(TTPlayer.NONE)
    }

    fun missingForWin(winCondition: TTWinCondition, player: TTPlayer, required: Int): Int? {
        fun missingInWindow(winnables: List<Winnable>): Int? {
            if (winnables.any { it.wonBy == player.next() || it.wonBy == TTPlayer.BLOCKED }) {
                return null
            }
            return winnables.count { it.wonBy == TTPlayer.NONE }
        }
        return winCondition.toList().windowed(required, 1, false)
            .map { it to missingInWindow(it) }
            .filter { it.second != null }
            .bestBy { -it.second!!.toDouble() }
            .map { it.second }
            .firstOrNull()
    }

    fun aiMove(model: TTController, depthRemainingBonus: Double): Point {
        val oppIndex = model.currentPlayer.playerIndex()
        val myPlayerIndex = model.currentPlayer.next().playerIndex()
        val heuristic: (TTController) -> Double = {state ->
            val opp = model.currentPlayer // 'I' just played so it's opponent's turn
            val me = opp.next()
            var result = 0.0
            if (state.isGameOver) {
                val winStatus = state.wonBy.toWinResult(myPlayerIndex)
                result = winStatus.result * 100
            } else {
                result = if (model is TTOthello) {
                    state.game.subs().sumByDouble {
                        it.wonBy.toWinResult(myPlayerIndex).result - it.wonBy.toWinResult(oppIndex).result
                    } / 10.0 // Divide by 10 to work with lower numbers
                } else {
                    fun groupBy(who: TTPlayer): (TTWinCondition) -> Int? = {
                        if (model is TTClassicControllerWithGravity) {
                            missingForWin(it, who, 4)
                        } else { it.emptySpaces() }
                    }
                    val myWins = state.game.winConds.filter { it.isWinnable(me) }.groupBy(groupBy(me)).mapValues { it.value.size }
                    val opWins = state.game.winConds.filter { it.isWinnable(opp) }.groupBy(groupBy(opp)).mapValues { it.value.size }
                    val positive = (myWins[1]?:0) * 4 + (myWins[2]?:0) * 2 + (myWins[3]?:0) * 0.1
                    val negative = (opWins[1]?:0) * 4 + (opWins[2]?:0) * 2 + (opWins[3]?:0) * 0.1
                    positive - negative
                }
            }
            -result // TODO: Remove double-negations (wrong "myPlayer" and maybe also "opp")
        }
        val ai = AlphaBeta(actions, branching, terminalState, heuristic, depthRemainingBonus)

        val options = runBlocking {
            actions(model).pmap { action ->
                try {
                    val newState = branching(model, action)
                    action to ai.score(newState, level)
                } catch (e: Exception) {
                    e.printStackTrace()
                    action to -1000.0
                }
            }.toList()
        }

        val move = options.bestBy { it.second }.random()
        println("Move results: $options. Best is $move")
        return move.first
    }
}

