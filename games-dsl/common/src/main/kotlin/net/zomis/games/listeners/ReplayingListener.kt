package net.zomis.games.listeners

import kotlinx.coroutines.CoroutineScope
import net.zomis.games.dsl.GameListener
import net.zomis.games.dsl.ReplayData
import net.zomis.games.dsl.impl.FlowStep

class ReplayingListener(private val data: ReplayData): GameListener {
    private var moves = 0
    private val replayMoveCount = data.actions.size

    override suspend fun handle(coroutineScope: CoroutineScope, step: FlowStep) {
        if (moves >= replayMoveCount) return
        when (step) {
            is FlowStep.IllegalAction -> throw IllegalStateException("$step was not replayed correctly after $moves moves")
            is FlowStep.PreMove -> {
                step.state.clear()
                step.state.putAll(data.actions.getOrNull(moves++)?.state ?: emptyMap())
            }
            is FlowStep.PreSetup<*> -> {
                step.state.clear()
                step.state.putAll(data.initialState ?: emptyMap())
            }
            else -> {}
        }
    }
}