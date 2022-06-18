package net.zomis.games.dsl.flow

import kotlinx.coroutines.runBlocking
import net.zomis.games.common.Point
import net.zomis.games.dsl.ActionType
import net.zomis.games.dsl.Actionable
import net.zomis.games.dsl.GameReplayableImpl
import net.zomis.games.dsl.impl.FlowStep
import net.zomis.games.dsl.impl.Game
import net.zomis.games.dsl.impl.GameController

class GameFlowTestHelper<T: Any>(val gameFlow: GameFlowImpl<T>) {
    suspend fun takeUntil(condition: (FlowStep) -> Boolean): FlowStep {
        while (true) {
            val output = gameFlow.feedbackReceiver.receive()
            println("Test Received: $output")
            if (condition.invoke(output)) {
                return output
            }
        }
    }
}

class GameFlowTestReplayable<T: Any>(val replayableImpl: GameReplayableImpl<T>) {
    val game: Game<T> get() = replayableImpl.game
    fun playThrough(function: () -> Actionable<T, Any>) {
        runBlocking { replayableImpl.playThrough(function) }
    }
    fun playThroughWithControllers(dynamicControllers: (Int) -> GameController<T>) {
        runBlocking { replayableImpl.playThroughWithControllers(dynamicControllers) }
    }
    fun <A: Any> action(playerIndex: Int, actionType: ActionType<T, A>, parameter: A) {
        runBlocking { replayableImpl.action(playerIndex, actionType, parameter) }
    }
    fun <A: Any> actionSerialized(playerIndex: Int, actionType: ActionType<T, A>, serialized: Any) {
        runBlocking { replayableImpl.actionSerialized(playerIndex, actionType, serialized) }
    }

    fun perform(action: Actionable<T, Any>) {
        runBlocking { replayableImpl.perform(action) }
    }
}

fun <T: Any> GameReplayableImpl<T>.runBlocking(): GameFlowTestReplayable<T> {
    return GameFlowTestReplayable(this)
}
