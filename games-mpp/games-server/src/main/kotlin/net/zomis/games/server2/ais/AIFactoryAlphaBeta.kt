package net.zomis.games.server2.ais

import klog.KLoggers
import kotlinx.coroutines.runBlocking
import net.zomis.bestBy
import net.zomis.common.pmap
import net.zomis.core.events.EventSystem
import net.zomis.games.ais.AlphaBeta
import net.zomis.games.ais.noAvailableActions
import net.zomis.games.dsl.Actionable
import net.zomis.games.dsl.impl.Game
import net.zomis.games.server2.games.PlayerGameMoveRequest

data class AIAlphaBetaConfig<T: Any>(val factory: AlphaBetaAIFactory<T>, val level: Int, val speedMode: AlphaBetaSpeedMode) {

    private val terminalState: (Game<T>) -> Boolean = { it.isGameOver() }

    fun evaluateActions(game: Game<T>, playerIndex: Int): List<Pair<Actionable<T, Any>, Double>> {
        val alphaBeta = createAlphaBeta(playerIndex)
        return runBlocking {
            actions(game).pmap { action ->
                val newState = branching(game, action)
                action to alphaBeta.score(newState, level)
            }.toList()
        }
    }

    fun evaluateState(game: Game<T>, playerIndex: Int): Double {
        return createAlphaBeta(playerIndex).heuristic(game)
    }

    private val actions: (Game<T>) -> List<Actionable<T, Any>> = {
        val players = 0 until it.playerCount
        players.flatMap { actionPlayer ->
            it.actions.types().flatMap {
                at -> at.availableActions(actionPlayer, null)
            }
        }
    }

    private val branching: (Game<T>, Actionable<T, Any>) -> Game<T> = { oldGame, action ->
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

    fun createAlphaBeta(playerIndex: Int): AlphaBeta<Game<T>, Actionable<T, Any>> {
        val heuristic2: (Game<T>) -> Double = {
            val elimination = it.eliminations.eliminations().find { elim -> elim.playerIndex == playerIndex }
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
        ServerAI(listOf(factory.gameType), factory.aiName(depth, speedMode), listenerFactory = { _, _ -> null }) {
            val model = serverGame.obj!! as Game<S>
            if (noAvailableActions(model, playerIndex)) {
                return@ServerAI null
            }

            logger.info { "Evaluating AlphaBeta options for ${factory.gameType} $depth" }

            val options = alphaBetaConfig.evaluateActions(model, playerIndex)
            val move = options.bestBy { it.second }.random()
            return@ServerAI PlayerGameMoveRequest(client, serverGame, playerIndex, move.first.actionType, move.first.parameter, false)
        }.register(events)
    }

}