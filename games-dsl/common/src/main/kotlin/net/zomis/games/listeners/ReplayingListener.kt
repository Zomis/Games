package net.zomis.games.listeners

import kotlinx.coroutines.CoroutineScope
import net.zomis.games.dsl.GameListener
import net.zomis.games.dsl.ReplayData
import net.zomis.games.dsl.impl.FlowStep

class ReplayingListener(private val data: ReplayData): GameListener {
    private var nextActionState = -1 // We still need to do the GameSetup
    private val replayActionsCount = data.actions.size

    override suspend fun handle(coroutineScope: CoroutineScope, step: FlowStep) {
        if (nextActionState >= replayActionsCount) return
        when (step) {
            is FlowStep.IllegalAction -> throw IllegalStateException("$step was not replayed correctly after $nextActionState actions")
            is FlowStep.PreMove -> {
                step.state.clear()
                step.state.putAll(data.actions.getOrNull(nextActionState++)?.state ?: emptyMap())
            }
            is FlowStep.PreSetup<*> -> {
                step.state.clear()
                step.state.putAll(data.initialState ?: emptyMap())
                nextActionState++
            }
            else -> {}
        }
    }
}