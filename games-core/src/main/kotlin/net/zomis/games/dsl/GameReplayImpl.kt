package net.zomis.games.dsl

import net.zomis.games.PlayerElimination
import net.zomis.games.dsl.flow.GameFlowContext
import net.zomis.games.dsl.flow.GameFlowImpl
import net.zomis.games.dsl.impl.*

/*
* Classes and utilities for programmatically replaying a game situation.
*
* For reproducible tests and simplified control of game
*/


typealias GameSituationState = Map<String, Any>?
open class GameplayCallbacks<T : Any> {
    open fun startState(setStateCallback: (GameSituationState) -> Unit) {/* empty by default */}
    open fun startedState(playerCount: Int, config: Any, state: GameSituationState) {/* empty by default */}
    open fun onPreMove(actionIndex: Int, action: Actionable<T, Any>, setStateCallback: (GameSituationState) -> Unit) {/* empty by default */}
    open fun onMove(actionIndex: Int, action: Actionable<T, Any>, actionReplay: ActionReplay) {/* empty by default */}
    open fun onElimination(elimination: PlayerElimination) {/* empty by default */}
    open fun onLog(log: List<ActionLogEntry>) {/* empty by default */}
}
data class ActionReplay(val actionType: String, val playerIndex: Int, val serializedParameter: Any, val state: GameSituationState)
data class ReplayData(
    val gameType: String,
    val playerCount: Int,
    val config: Any,
    val initialState: GameSituationState,
    val actions: List<ActionReplay>
)

class GameReplayableImpl<T : Any>(
    gameSpec: GameSpec<T>,
    val playerCount: Int,
    options: Any? = null,
    val gameplayCallbacks: GameplayCallbacks<T>
) {

    val setup = GameSetupImpl(gameSpec)
    val state = StateKeeper().also { stateKeeper ->
        var curr: GameSituationState = null
        gameplayCallbacks.startState { curr = it }
        curr?.also { stateKeeper.setState(it) }
    }
    val config = options ?: setup.getDefaultConfig()
    val game = setup.createGameWithState(playerCount, config, state).also {
        gameplayCallbacks.startedState(playerCount, config, it.stateKeeper.lastMoveState())
    }
    var actionIndex: Int = 0
        private set

    suspend fun playThrough(function: () -> Actionable<T, Any>) {
        if (game is GameFlowImpl) {
            for (feedback in game.feedbackReceiver) {
                println("GameReplayImpl Playthrough begin: $feedback")
                if (feedback is GameFlowContext.Steps.AwaitInput) {
                    break
                }
            }
        }
        while (!game.isGameOver()) {
            perform(function())
        }
    }

    suspend fun playThroughWithControllers(dynamicControllers: (Int) -> GameController<T>) {
        val contexts = game.playerIndices.map { GameControllerContext(game, it) }
        while (!game.isGameOver()) {
            contexts.forEach { context ->
                val controller = dynamicControllers.invoke(context.playerIndex)
                val controllerResult = controller.invoke(context)
                if (controllerResult != null) {
                    perform(controllerResult)
                }
            }
        }
    }

    suspend fun <A: Any> action(playerIndex: Int, actionType: ActionType<T, A>, parameter: A) {
        perform(game.actions.type(actionType)!!.createAction(playerIndex, parameter) as Actionable<T, Any>)
    }

    suspend fun <A: Any> actionSerialized(playerIndex: Int, actionType: ActionType<T, A>, serialized: Any) {
        perform(game.actions.type(actionType)!!.createActionFromSerialized(playerIndex, serialized) as Actionable<T, Any>)
    }

    suspend fun perform(action: Actionable<T, Any>) {
        if (game.eliminations.eliminationFor(action.playerIndex) != null) {
            throw IllegalArgumentException("Player ${action.playerIndex} is already eliminated.")
        }
        if (game is GameFlowImpl<T>) {
            this.performGameFlow(action)
        } else {
            this.performDirect(action)
        }
    }

    private suspend fun performGameFlow(action: Actionable<T, Any>) {
        val gameFlow = game as GameFlowImpl<T>
        println("GameReplayImpl Send Action: $action")
        gameFlow.actionsInput.send(action)
        for (feedback in gameFlow.feedbackReceiver) {
            println("GameReplayImpl Feedback: $feedback")
            when (feedback) {
                is GameFlowContext.Steps.NextView -> {}
                is GameFlowContext.Steps.Elimination -> gameplayCallbacks.onElimination(feedback.elimination)
                is GameFlowContext.Steps.Log -> gameplayCallbacks.onLog(listOf(feedback.log))
                is GameFlowContext.Steps.GameSetup -> gameplayCallbacks.startedState(feedback.playerCount, feedback.config, feedback.state)
                is GameFlowContext.Steps.ActionPerformed<*> -> {
                    val actionReplay = ActionReplay(action.actionType, action.playerIndex,
                        feedback.actionImpl.actionType.serialize(action.parameter), game.stateKeeper.lastMoveState()
                    )
                    gameplayCallbacks.onMove(actionIndex, action, actionReplay)
                    this.actionIndex++
                }
                is GameFlowContext.Steps.AwaitInput -> return
                else -> {}
            }
        }
    }

    private fun performDirect(action: Actionable<T, Any>) {
        game.stateKeeper.clear()
        gameplayCallbacks.onPreMove(this.actionIndex, action) {
            if (it != null) state.setState(it)
        }
        val eliminatedBefore = game.eliminations.eliminations()
        val actionImpl = game.actions.type(action.actionType)!!
        actionImpl.perform(action)

        // Collect state and logs. Perform callbacks.
        val actionReplay = ActionReplay(action.actionType, action.playerIndex,
            actionImpl.actionType.serialize(action.parameter), game.stateKeeper.lastMoveState()
        )
        gameplayCallbacks.onMove(actionIndex, action, actionReplay)
        gameplayCallbacks.onLog(game.stateKeeper.logs())
        val newlyEliminated = game.eliminations.eliminations() - eliminatedBefore
        newlyEliminated.forEach { gameplayCallbacks.onElimination(it) }

        this.actionIndex++
    }

}
