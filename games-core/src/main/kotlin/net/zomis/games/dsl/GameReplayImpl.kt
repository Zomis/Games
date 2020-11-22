package net.zomis.games.dsl

import net.zomis.games.PlayerElimination
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

    fun playThrough(function: () -> Actionable<T, Any>) {
        while (!game.isGameOver()) {
            perform(function())
        }
    }

    fun playThroughWithControllers(dynamicControllers: (Int) -> GameController<T>) {
        val contexts = game.playerIndices.map { GameControllerContext(game, it) }
        while (!game.isGameOver()) {
            contexts.forEach { context ->
                val controller = dynamicControllers(context.playerIndex)
                val controllerResult = controller(context)
                if (controllerResult != null) {
                    perform(controllerResult)
                }
            }
        }
    }

    fun <A: Any> action(playerIndex: Int, actionType: ActionType<T, A>, parameter: A) {
        perform(game.actions.type(actionType)!!.createAction(playerIndex, parameter) as Actionable<T, Any>)
    }

    fun <A: Any> actionSerialized(playerIndex: Int, actionType: ActionType<T, A>, serialized: Any) {
        perform(game.actions.type(actionType)!!.createActionFromSerialized(playerIndex, serialized) as Actionable<T, Any>)
    }

    fun perform(action: Actionable<T, Any>) {
        if (game.eliminations.eliminationFor(action.playerIndex) != null) {
            throw IllegalArgumentException("Player ${action.playerIndex} is already eliminated.")
        }
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
