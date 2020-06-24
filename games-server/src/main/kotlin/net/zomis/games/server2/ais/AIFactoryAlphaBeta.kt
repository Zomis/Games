package net.zomis.games.server2.ais

import klog.KLoggers
import kotlinx.coroutines.runBlocking
import net.zomis.bestBy
import net.zomis.common.pmap
import net.zomis.core.events.EventSystem
import net.zomis.games.ais.AlphaBeta
import net.zomis.games.dsl.Actionable
import net.zomis.games.dsl.impl.GameImpl
import net.zomis.games.server2.games.PlayerGameMoveRequest

data class AIAlphaBetaConfig<T: Any>(val factory: AlphaBetaAIFactory<T>, val level: Int, val speedMode: AlphaBetaSpeedMode) {

    private val terminalState: (GameImpl<T>) -> Boolean = { it.isGameOver() }

    fun evaluateActions(game: GameImpl<T>, playerIndex: Int): List<Pair<Actionable<T, Any>, Double>> {
        val alphaBeta = createAlphaBeta(playerIndex)
        return runBlocking {
            actions(game).pmap { action ->
                val newState = branching(game, action)
                action to alphaBeta.score(newState, level)
            }.toList()
        }
    }

    fun evaluateState(game: GameImpl<T>, playerIndex: Int): Double {
        return createAlphaBeta(playerIndex).heuristic(game)
    }

    private val actions: (GameImpl<T>) -> List<Actionable<T, Any>> = {
        val players = 0 until it.playerCount
        players.flatMap { actionPlayer ->
            it.actions.types().flatMap {
                at -> at.availableActions(actionPlayer)
            }
        }
    }

    private val branching: (GameImpl<T>, Actionable<T, Any>) -> GameImpl<T> = { oldGame, action ->
        val copy = oldGame.copy(factory.copier)
        val actionType = copy.actions.type(action.actionType)!!
        val serializedAction = actionType.actionType.serialize(action.parameter)
        val actionCopy = actionType.createActionFromSerialized(action.playerIndex, serializedAction)
        if (!actionType.isAllowed(actionCopy)) {
            throw Exception("Not allowed to perform $action in ${copy.view(null)}")
        }
        actionType.perform(actionCopy)
        copy
    }

    fun createAlphaBeta(playerIndex: Int): AlphaBeta<GameImpl<T>, Actionable<T, Any>> {
        val heuristic2: (GameImpl<T>) -> Double = {
            val elimination = it.eliminationCallback.eliminations().find { elim -> elim.playerIndex == playerIndex }
            if (elimination != null) {
                elimination.winResult.result * 1_000_000.0
            } else factory.heuristic(it, playerIndex)
        }
        return AlphaBeta(actions, branching, terminalState, heuristic2, speedMode.depthRemainingBonus)
    }

}
class AIFactoryAlphaBeta {

    private val logger = KLoggers.logger(this)

    fun <S: Any> createAlphaBetaAI(factory: AlphaBetaAIFactory<S>, events: EventSystem, depth: Int, speedMode: AlphaBetaSpeedMode) {
        val alphaBetaConfig = AIAlphaBetaConfig(factory, depth, speedMode)
        ServerAI(factory.gameType, factory.aiName(depth, speedMode)) { game, index ->
            val model = game.obj as GameImpl<S>
            if (noAvailableActions(model, index)) {
                return@ServerAI emptyList()
            }

            logger.info { "Evaluating AlphaBeta options for ${factory.gameType} $depth" }

            val options = alphaBetaConfig.evaluateActions(model, index)
            val move = options.bestBy { it.second }.random()
            return@ServerAI listOf(PlayerGameMoveRequest(game, index, move.first.actionType, move.first.parameter))
        }.register(events)
    }

}