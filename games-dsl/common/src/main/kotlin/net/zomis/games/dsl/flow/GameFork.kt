package net.zomis.games.dsl.flow

import net.zomis.games.dsl.ActionType
import net.zomis.games.dsl.ReplayData
import net.zomis.games.dsl.impl.Game
import net.zomis.games.dsl.listeners.BlockingGameListener

class GameForkContext<T: Any>(val gameFork: GameForkResult<T>): GameForkScope<T> {
    override suspend fun <A : Any> performAction(actionType: ActionType<T, A>, playerIndex: Int, parameter: A) {
        gameFork.blockingGameListener.awaitAndPerform(playerIndex, actionType, parameter)
    }
}

class GameForkResult<T: Any>(
    val game: Game<T>,
    val blockingGameListener: BlockingGameListener,
    var allowForks: Boolean = false,
    val replayData: ReplayData
) {
    override fun toString(): String = "GameForkResult($game $blockingGameListener $replayData)"
}

interface GameForkScope<T: Any> {
    suspend fun <A: Any> performAction(actionType: ActionType<T, A>, playerIndex: Int, parameter: A)
}
