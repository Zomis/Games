package net.zomis.games.dsl

import net.zomis.games.dsl.impl.GameImpl

private class ReplayCallback<T : Any>(private val replayData: ReplayData) : GameplayCallbacks<T>() {
    override fun startState(setStateCallback: (GameSituationState) -> Unit) {
        setStateCallback(replayData.initialState)
    }

    override fun onPreMove(actionIndex: Int, action: Actionable<T, Any>, setStateCallback: (GameSituationState) -> Unit) {
        setStateCallback(replayData.actions[actionIndex].state)
    }
}
class ReplayException(override val message: String?, override val cause: Throwable?): Exception(message, cause)

class Replay<T : Any>(
    gameSpec: GameSpec<T>,
    val playerCount: Int,
    val options: Any?,
    private val replayData: ReplayData
) {

    fun goToStart(): Replay<T> {
        this.setPosition(0)
        return this
    }

    fun goToEnd(): Replay<T> {
        this.setPosition(replayData.actions.size)
        return this
    }

    private val entryPoint = GamesImpl.game(gameSpec)
    lateinit var gameReplayable: GameReplayableImpl<T>
    private var position: Int = 0
    val game: GameImpl<T> get() = gameReplayable.game

    private fun setPosition(newPosition: Int) {
        if (newPosition < this.position) {
            restart()
        }
        while (newPosition > this.position) {
            stepForward()
        }
    }

    private fun stepForward() {
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
        gameReplayable = entryPoint.replayable(playerCount, options, ReplayCallback(replayData))
        this.position = 0
    }

    init {
        restart()
    }

}
