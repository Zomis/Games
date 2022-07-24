package net.zomis.games.dsl.flow

import kotlinx.coroutines.CoroutineScope
import net.zomis.games.dsl.Actionable

class GameFlowStepImpl<T: Any>(
    private val gameFlow: GameFlowImpl<T>,
    private val coroutineScope: CoroutineScope,
    private val name: String,
    private val step: suspend GameFlowStepScope<T>.() -> Unit
): GameFlowStep<T> {

    override var action: Actionable<T, Any>? = null
    override suspend fun loopUntil(function: GameFlowStep<T>.() -> Boolean) {
        while (!gameFlow.isGameOver() && !function()) {
            runDsl()
        }
    }

    suspend fun runDsl() {
        // Run step at least once
        // Return GameFlowStep with the possibility of running it again, or just returning the action performed
        println("GameFlow Coroutine step $name")
        val child = GameFlowContext(coroutineScope, gameFlow, "${this.name}/$name", false)
        step.invoke(child)
        println("GameFlow Coroutine step sendFeedbacks")
        gameFlow.sendFeedbacks()
        println("GameFlow Coroutine step send AwaitInput")
        action = gameFlow.nextAction()
    }

}
