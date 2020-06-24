package net.zomis.games.server2.games.impl

import klog.KLoggers
import kotlinx.coroutines.runBlocking
import net.zomis.bestBy
import net.zomis.common.pmap
import net.zomis.games.ais.AlphaBeta
import net.zomis.games.common.Point
import net.zomis.tttultimate.TTFactories
import net.zomis.tttultimate.TTPlayer
import net.zomis.tttultimate.TTWinCondition
import net.zomis.tttultimate.Winnable
import net.zomis.tttultimate.games.*

object TTConnect4AlphaBeta {

    fun emptySpaces(it: TTWinCondition): Int {
        return it.hasCurrently(TTPlayer.NONE)
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

}

class TTAlphaBeta(val level: Int, val heuristic: (model: TTController, myPlayer: TTPlayer) -> Double) {

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
        copied.makeMoves(history)
        return copied
    }

    val actions: (TTController) -> List<Point> = { model ->
        var subs = model.game.subs()
        if (model is TTUltimateController) {
            subs = subs.flatMap { it.subs() }
        }
        subs.filter { model.isAllowedPlay(it) }.map { Point(it.globalX, it.globalY) }
    }
    val branching: (TTController, Point) -> TTController = { game, move ->
        val copy = this.copy(game)
        copy.play(copy.game.getSmallestTile(move.x, move.y))
        copy
    }
    val terminalState: (TTController) -> Boolean = { it.isGameOver }

    fun aiMove(model: TTController, depthRemainingBonus: Double): Point {
        val oppIndex = model.currentPlayer.playerIndex()
        val myPlayer = model.currentPlayer.next()
        val myPlayerIndex = myPlayer.playerIndex()
        val heuristic: (TTController) -> Double = {state ->
            var result = if (state.isGameOver) {
                val winStatus = state.wonBy.toWinResult(myPlayerIndex)
                winStatus.result * 100
            } else {
                heuristic(model, myPlayer)
            }
            result
        }
        val ai = AlphaBeta(actions, branching, terminalState, heuristic, depthRemainingBonus)

        val availableActions = actions(model)
        val options = runBlocking {
            availableActions.pmap { action ->
                try {
                    val newState = branching(model, action)
                    action to ai.score(newState, level)
                } catch (e: Exception) {
                    logger.error(e, "Unable to determine value of action $action")
                    action to -1000.0
                }
            }.toList()
        }

        val move = options.bestBy { it.second }.random()
        logger.info { "Move results: $options. Best is $move" }
        return move.first
    }
}

