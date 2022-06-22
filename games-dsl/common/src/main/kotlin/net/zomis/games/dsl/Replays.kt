package net.zomis.games.dsl

import net.zomis.games.dsl.impl.Game

@Deprecated("Replaced by GameListeners")
private class ReplayCallback<T : Any>(private val replayData: ReplayData) : GameplayCallbacks<T>() {
    override fun startState(setStateCallback: (GameSituationState) -> Unit) = setStateCallback(replayData.initialState)
    override fun onPreMove(actionIndex: Int, action: Actionable<T, Any>, setStateCallback: (GameSituationState) -> Unit)
        = setStateCallback(replayData.actions[actionIndex].state)
}
class ReplayException(override val message: String?, override val cause: Throwable?): Exception(message, cause)

class Replay<T : Any>(
    gameSpec: GameSpec<T>,
    val playerCount: Int,
    val config: GameConfigs,
    private val replayData: ReplayData
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
        gameReplayable = entryPoint.replayable(playerCount, config, ReplayCallback(replayData))
        this.position = 0
    }

    fun replayable(): GameReplayableImpl<T> = gameReplayable

    init {
        restart()
    }

}
