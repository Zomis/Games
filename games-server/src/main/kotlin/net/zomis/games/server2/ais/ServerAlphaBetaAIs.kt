package net.zomis.games.server2.ais

import klog.KLoggers
import net.zomis.core.events.EventSystem
import net.zomis.games.dsl.impl.GameImpl
import net.zomis.games.impl.TTQuixoController
import net.zomis.games.impl.ttt.TTT3D
import net.zomis.games.impl.ttt.TTT3DPiece
import net.zomis.games.server2.games.GameTypeRegisterEvent
import net.zomis.games.server2.games.impl.TTConnect4AlphaBeta
import net.zomis.games.server2.games.impl.toWinResult
import net.zomis.tttultimate.TTBase
import net.zomis.tttultimate.TTPlayer
import net.zomis.tttultimate.TTWinCondition
import net.zomis.tttultimate.games.TTClassicControllerWithGravity
import net.zomis.tttultimate.games.TTController

enum class AlphaBetaSpeedMode(val nameSuffix: String, val depthRemainingBonus: Double) {
    NORMAL("", 0.0),
    SLOW("_Evil", -0.01),
    QUICK("_Nice", 0.01),
}
typealias AlphaBetaCopier<S> = (old: S, copy: S) -> Unit
data class AlphaBetaAIFactory<S: Any>(
    val copier: AlphaBetaCopier<S>,
    val gameType: String,
    val namePrefix: String,
    val maxLevel: Int,
    val useSpeedModes: Boolean,
    val heuristic: (GameImpl<S>, Int) -> Double
) {
    fun aiName(level: Int, speedMode: AlphaBetaSpeedMode)
        = "#AI_${this.namePrefix}_" + this.gameType + "_" + level + speedMode.nameSuffix
}

class ServerAlphaBetaAIs(private val aiRepository: AIRepository) {
    private val logger = KLoggers.logger(this)

    fun heuristicTTT(state: TTController, myIndex: Int): Double {
        val me = if (myIndex == 0) TTPlayer.X else TTPlayer.O
        val opp = me.next()
        if (state.isGameOver) {
            return state.wonBy.toWinResult(myIndex).result * 100
        }
        fun groupBy(who: TTPlayer): (TTWinCondition) -> Int? = {
            if (state is TTClassicControllerWithGravity) {
                TTConnect4AlphaBeta.missingForWin(it, who, 4)
            } else { TTConnect4AlphaBeta.emptySpaces(it) }
        }
        val myWins = state.game.winConds.filter { it.isWinnable(me) }.groupBy(groupBy(me)).mapValues { it.value.size }
        val opWins = state.game.winConds.filter { it.isWinnable(opp) }.groupBy(groupBy(opp)).mapValues { it.value.size }
        val positive = (myWins[1]?:0) * 4 + (myWins[2]?:0) * 2 + (myWins[3]?:0) * 0.1
        val negative = (opWins[1]?:0) * 4 + (opWins[2]?:0) * 2 + (opWins[3]?:0) * 0.1
        return positive - negative
    }

    fun heuristicTTT3D(game: TTT3D, myIndex: Int): Double {
        val me = if (myIndex == 0) TTT3DPiece.X else TTT3DPiece.O
        val opp = me.opponent()
        var result = if (game.findWinner() != null) {
            if (game.findWinner() == me) 100.0 else -100.0
        } else {
            val myWins = game.winConditions.filter { it.canWin(me) }.groupBy { it.emptySpaces() }.mapValues { it.value.size }
            val opWins = game.winConditions.filter { it.canWin(opp) }.groupBy { it.emptySpaces() }.mapValues { it.value.size }
            val positive = (myWins[1]?:0) * 4 + (myWins[2]?:0) * 2 + (myWins[3]?:0) * 0.1
            val negative = (opWins[1]?:0) * 4 + (opWins[2]?:0) * 2 + (opWins[3]?:0) * 0.1
            positive - negative
        }
        return result
    }

    fun heuristicQuixo(state: TTQuixoController, myIndex: Int): Double {
        val me = if (myIndex == 0) TTPlayer.X else TTPlayer.O
        val opp = me.next()
        if (state.board.isWon) {
            return state.board.wonBy.toWinResult(myIndex).result * 100
        }
        fun groupBy(who: TTPlayer): (TTWinCondition) -> Int? = {
            TTConnect4AlphaBeta.emptySpaces(it)
        }
        val myWins = state.board.winConds.filter { it.isWinnable(me) }.groupBy(groupBy(me)).mapValues { it.value.size }
        val opWins = state.board.winConds.filter { it.isWinnable(opp) }.groupBy(groupBy(opp)).mapValues { it.value.size }
        val positive = (myWins[1]?:0) * 4 + (myWins[2]?:0) * 2 + (myWins[3]?:0) * 0.1
        val negative = (opWins[1]?:0) * 4 + (opWins[2]?:0) * 2 + (opWins[3]?:0) * 0.1
        return positive - negative
    }

    fun heuristicTileCount(game: TTController, myIndex: Int): Double {
        return game.game.subs().sumByDouble {
            it.wonBy.toWinResult(myIndex).result - it.wonBy.toWinResult(1 - myIndex).result
        } / 10.0 // Divide by 10 to work with lower numbers
    }

    fun <T: Any> model(modelHeuristic: (game: T, myIndex: Int) -> Double): (GameImpl<T>, Int) -> Double =
            { gameImpl, index -> modelHeuristic(gameImpl.model, index) }

    fun setup(events: EventSystem) {
        val ttAB: AlphaBetaCopier<TTController> = { old, copy ->
            val moves = old.saveHistory()
            copy.makeMoves(moves)
        }
        val quixoAB = { old: TTQuixoController, copy: TTQuixoController ->
            copy.currentPlayer = old.currentPlayer
            fun recursiveSet(source: TTBase, destination: TTBase) {
                destination.subs().forEach {
                    val sourceSub = source.getSub(it.x, it.y)!!
                    it.setPlayedBy(sourceSub.wonBy)
                    if (it.hasSubs()) {
                        recursiveSet(sourceSub, it)
                    }
                }
            }
            recursiveSet(old.board, copy.board)
        }
        val tt3Dab = { old: TTT3D, copy: TTT3D ->
            copy.currentPlayer = old.currentPlayer
            copy.allFields().forEach { it.piece = old.get(it.y, it.x, it.z) }
        }
        val aiFactories = listOf<AlphaBetaAIFactory<out Any>>(
            AlphaBetaAIFactory(ttAB,"DSL-TTT", "AlphaBeta",6, false, model(::heuristicTTT)),
            AlphaBetaAIFactory(ttAB,"DSL-Connect4", "AlphaBeta", 5, true, model(::heuristicTTT)),
            AlphaBetaAIFactory(ttAB,"DSL-UTTT", "AlphaBeta", 3, false, model(::heuristicTTT)),
            AlphaBetaAIFactory(ttAB,"DSL-Reversi", "AlphaBeta", 5, false, model(::heuristicTileCount)),
            AlphaBetaAIFactory(quixoAB,"Quixo", "AlphaBeta", 3, false, model(::heuristicQuixo)),
            AlphaBetaAIFactory(tt3Dab, "DSL-TTT3D", "AlphaBeta", 5, true, model(::heuristicTTT3D))
        )

        events.listen("register AlphaBeta for TTController-games", GameTypeRegisterEvent::class, { event ->
            aiFactories.any { it.gameType == event.gameType }
        }, {event ->
            aiFactories.filter { it.gameType == event.gameType }.forEach {factory: AlphaBetaAIFactory<out Any> ->
                aiRepository.createAlphaBetaAIs(events, factory)
            }
        })
    }
}