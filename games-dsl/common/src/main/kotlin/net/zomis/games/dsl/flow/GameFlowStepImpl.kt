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
        val child = GameFlowContext(coroutineScope, gameFlow, "${this.name}/$name")
        step.invoke(child)
        gameFlow.sendFeedbacks()
        action = gameFlow.nextAction()
    }

}
