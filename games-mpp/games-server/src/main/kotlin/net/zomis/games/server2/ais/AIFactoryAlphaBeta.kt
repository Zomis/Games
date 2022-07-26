package net.zomis.games.server2.ais

import net.zomis.common.pmap
import net.zomis.games.ais.AlphaBeta
import net.zomis.games.dsl.Actionable
import net.zomis.games.dsl.impl.Game
import net.zomis.games.dsl.impl.GameImpl

data class AIAlphaBetaConfig<T: Any>(val factory: AlphaBetaAIFactory<T>, val level: Int, val speedMode: AlphaBetaSpeedMode) {

    private val terminalState: (Game<T>) -> Boolean = { it.isGameOver() }

    suspend fun evaluateActions(game: Game<T>, playerIndex: Int): List<Pair<Actionable<T, Any>, Double>> {
        val alphaBeta = createAlphaBeta(playerIndex)
        return actions(game).pmap { action ->
            val newState = branching(game, action)
            action to alphaBeta.score(newState, level)
        }.toList()
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

    private val branching: suspend (Game<T>, Actionable<T, Any>) -> Game<T> = { oldGame, action ->
        if (factory.copier != null) {
            // This method of copying is a lot quicker
            require(oldGame is GameImpl<*>)
            val oldG = oldGame as GameImpl<T>
            val copiedGame = oldG.quickCopy(factory.copier)
            val actionType = copiedGame.actions.type(action.actionType)!!
            val serializedAction = actionType.actionType.serialize(action.parameter)
            val actionCopy = actionType.createActionFromSerialized(action.playerIndex, serializedAction)
            if (!actionType.isAllowed(actionCopy)) {
                throw Exception("Not allowed to perform $action in ${copiedGame.view(null)}")
            }
            actionType.perform(actionCopy)
            copiedGame
        } else {
            val fork = oldGame.copy()
            val forkedGame = fork.game
            fork.blockingGameListener.await()
            val actionType = forkedGame.actions.type(action.actionType)!!
            val serializedAction = actionType.actionType.serialize(action.parameter)
            val actionCopy = actionType.createActionFromSerialized(action.playerIndex, serializedAction)
            if (!actionType.isAllowed(actionCopy)) {
                val allowed = actionType.isAllowed(actionCopy)
                println("Not allowed ($allowed) from $oldGame in $fork to perform $action in ${forkedGame.view(null)}")
                throw Exception("Not allowed ($allowed) from $oldGame to perform $action in ${forkedGame.view(null)}")
            }
            fork.blockingGameListener.awaitAndPerform(actionCopy)
            fork.blockingGameListener.await()
            forkedGame.stop()
            forkedGame
        }
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
