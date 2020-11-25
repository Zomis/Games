package net.zomis.games.dsl

import net.zomis.games.dsl.impl.Game

private class PostReplayCallback<T : Any>(private val replayActionCount: Int, private val postReplayMoveCallback: GameplayCallbacks<T>): GameplayCallbacks<T>() {
    override fun onPreMove(actionIndex: Int, action: Actionable<T, Any>, setStateCallback: (GameSituationState) -> Unit) {
        if (actionIndex >= replayActionCount) {
            postReplayMoveCallback.onPreMove(actionIndex, action, setStateCallback)
        }
    }

    override fun onMove(actionIndex: Int, action: Actionable<T, Any>, actionReplay: ActionReplay) {
        if (actionIndex >= replayActionCount) {
            postReplayMoveCallback.onMove(actionIndex, action, actionReplay)
        }
    }
}

private class ReplayCallback<T : Any>(private val replayData: ReplayData) : GameplayCallbacks<T>() {
    override fun startState(setStateCallback: (GameSituationState) -> Unit) = setStateCallback(replayData.initialState)
    override fun onPreMove(actionIndex: Int, action: Actionable<T, Any>, setStateCallback: (GameSituationState) -> Unit)
        = setStateCallback(replayData.actions[actionIndex].state)
}
class ReplayException(override val message: String?, override val cause: Throwable?): Exception(message, cause)

class Replay<T : Any>(
    gameSpec: GameSpec<T>,
    val playerCount: Int,
    val options: Any?,
    private val replayData: ReplayData,
    val postReplayMoveCallback: GameplayCallbacks<T>,
    val alwaysCallback: GameplayCallbacks<T>
) {

    suspend fun goToStart(): Replay<T> = this.gotoPosition(0)
    suspend fun goToEnd(): Replay<T> = this.gotoPosition(replayData.actions.size)

    private val entryPoint = GamesImpl.game(gameSpec)
    lateinit var gameReplayable: GameReplayableImpl<T>
    private var position: Int = 0
    val game: Game<T> get() = gameReplayable.game

    suspend fun gotoPosition(newPosition: Int): Replay<T> {
        if (newPosition < this.position) {
            restart()
        }
        while (newPosition > this.position) {
            stepForward()
        }
        return this
    }

    private suspend fun stepForward() {
        val action = replayData.actions[this.position]
        try {
            val actionable = gameReplayable.game.actions.type(action.actionType)!!
                    .createActionFromSerialized(action.playerIndex, action.serializedParameter)
            gameReplayable.perform(actionable)
        } catch (e: Exception) {
            throw ReplayException("Unable to perform action ${this.position}", e)
        }
        this.position++
    }

    private fun restart() {
        gameReplayable = entryPoint.replayable(playerCount, options, ReplayCallback(replayData), PostReplayCallback(replayData.actions.size, postReplayMoveCallback), alwaysCallback)
        this.position = 0
    }

    fun replayable(): GameReplayableImpl<T> = gameReplayable

    init {
        restart()
    }

}
