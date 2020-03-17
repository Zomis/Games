package net.zomis.games.server2.ais

import klog.KLoggers
import kotlinx.coroutines.runBlocking
import net.zomis.bestBy
import net.zomis.common.bmap
import net.zomis.common.pmap
import net.zomis.core.events.EventSystem
import net.zomis.games.WinResult
import net.zomis.games.ais.AlphaBeta
import net.zomis.games.dsl.Actionable
import net.zomis.games.dsl.impl.GameImpl
import net.zomis.games.dsl.sourcedest.TTArtax
import net.zomis.games.dsl.sourcedest.TTQuixoController
import net.zomis.games.impl.TTT3D
import net.zomis.games.impl.TTT3DPiece
import net.zomis.games.server2.games.GameTypeRegisterEvent
import net.zomis.games.server2.games.PlayerGameMoveRequest
import net.zomis.games.server2.games.impl.TTConnect4AlphaBeta
import net.zomis.games.server2.games.impl.toWinResult
import net.zomis.tttultimate.TTBase
import net.zomis.tttultimate.TTPlayer
import net.zomis.tttultimate.TTWinCondition
import net.zomis.tttultimate.games.TTClassicControllerWithGravity
import net.zomis.tttultimate.games.TTController

private enum class AlphaBetaSpeedMode(val nameSuffix: String, val depthRemainingBonus: Double) {
    NORMAL("", 0.0),
    SLOW("_Evil", -0.01),
    QUICK("_Nice", 0.01),
}
typealias AlphaBetaCopier<S> = (old: S, copy: S) -> Unit
data class AlphaBetaAIFactory<S>(
    val copier: AlphaBetaCopier<S>,
    val gameType: String,
    val maxLevel: Int,
    val useSpeedModes: Boolean,
    val heuristic: (S, Int) -> Double
)

class ServerAlphaBetaAIs {
    private val logger = KLoggers.logger(this)

    private fun <S: Any> createAlphaBetaAI(gameType: String, copier: (S, S) -> Unit, events: EventSystem, depth: Int, speedMode: AlphaBetaSpeedMode, heuristic: (S, Int) -> Double) {
        val terminalState: (GameImpl<S>) -> Boolean = { it.isGameOver() }
        ServerAI(gameType, "#AI_AlphaBeta_" + gameType + "_" + depth + speedMode.nameSuffix) { game, index ->
            val model = game.obj as GameImpl<S>
            if (noAvailableActions(model, index)) {
                return@ServerAI emptyList()
            }

            val actions: (GameImpl<S>) -> List<Actionable<S, Any>> = {
                val players = 0 until it.playerCount
                players.flatMap { actionPlayer ->
                    it.actions.types().flatMap {
                        at -> at.availableActions(actionPlayer)
                    }
                }
            }
            val branching: (GameImpl<S>, Actionable<S, Any>) -> GameImpl<S> = { oldGame, action ->
                val copy = oldGame.copy(copier)
                val actionType = copy.actions.type(action.actionType)!!
                val actionCopy = actionType.createAction(action.playerIndex, action.parameter)
                if (!actionType.isAllowed(actionCopy)) {
                    throw Exception("Not allowed to perform $action in ${copy.view(index)}")
                }
                actionType.perform(actionCopy)
                copy.stateCheck()
                copy
            }
            val heuristic2: (GameImpl<S>) -> Double = { heuristic(it.model, index) }
            val alphaBeta = AlphaBeta(actions, branching, terminalState, heuristic2, speedMode.depthRemainingBonus)
            logger.info { "Evaluating AlphaBeta options for $gameType $depth" }

            val options = runBlocking {
                actions(model).pmap { action ->
                    val newState = branching(model, action)
                    action to alphaBeta.score(newState, depth)
                }.toList()
            }
            val move = options.bestBy { it.second }.random()
            return@ServerAI listOf(PlayerGameMoveRequest(game, index, move.first.actionType, move.first.parameter))
        }.register(events)
    }

    private fun <T: Any> noAvailableActions(model: GameImpl<T>, index: Int): Boolean {
        return model.actions.types().all { it.availableActions(index).none() }
    }

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
        var result = 0.0
        if (game.findWinner() != null) {
            result = if (game.findWinner() == me) 100.0 else -100.0
        } else {
            val myWins = game.winConditions.filter { it.canWin(me) }.groupBy { it.emptySpaces() }.mapValues { it.value.size }
            val opWins = game.winConditions.filter { it.canWin(opp) }.groupBy { it.emptySpaces() }.mapValues { it.value.size }
            val positive = (myWins[1]?:0) * 4 + (myWins[2]?:0) * 2 + (myWins[3]?:0) * 0.1
            val negative = (opWins[1]?:0) * 4 + (opWins[2]?:0) * 2 + (opWins[3]?:0) * 0.1
            result = positive - negative
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
        val artaxAB = { old: TTArtax, copy: TTArtax ->
            copy.currentPlayer = old.currentPlayer
            copy.board.all().forEach { dest -> dest.value = old.board.get(dest.x, dest.y) }
        }
        val aiFactories = listOf(
            AlphaBetaAIFactory(ttAB,"DSL-TTT", 6, false, ::heuristicTTT),
            AlphaBetaAIFactory(ttAB,"DSL-Connect4", 5, true, ::heuristicTTT),
            AlphaBetaAIFactory(ttAB,"DSL-UTTT", 3, false, ::heuristicTTT),
            AlphaBetaAIFactory(ttAB,"DSL-Reversi", 5, false, ::heuristicTileCount),
            AlphaBetaAIFactory(quixoAB,"Quixo", 3, false, ::heuristicQuixo),
            AlphaBetaAIFactory(tt3Dab, "DSL-TTT3D", 5, true, ::heuristicTTT3D)
        )

        events.listen("register AlphaBeta for TTController-games", GameTypeRegisterEvent::class, { event ->
            aiFactories.any { it.gameType == event.gameType }
        }, {event ->
            aiFactories.filter { it.gameType == event.gameType }.forEach {factory ->
                (0 until factory.maxLevel).forEach {level ->
                    createAlphaBetaAI(event.gameType, factory.copier, events, level, AlphaBetaSpeedMode.NORMAL, factory.heuristic)
                }
                if (factory.useSpeedModes) {
                    createAlphaBetaAI(event.gameType, factory.copier, events, factory.maxLevel, AlphaBetaSpeedMode.QUICK, factory.heuristic)
                    createAlphaBetaAI(event.gameType, factory.copier, events, factory.maxLevel, AlphaBetaSpeedMode.SLOW, factory.heuristic)
                } else {
                    createAlphaBetaAI(event.gameType, factory.copier, events, factory.maxLevel, AlphaBetaSpeedMode.NORMAL, factory.heuristic)
                }
            }
        })
    }
}