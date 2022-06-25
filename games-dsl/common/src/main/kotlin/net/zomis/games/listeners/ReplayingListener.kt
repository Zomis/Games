package net.zomis.games.listeners

import kotlinx.coroutines.CoroutineScope
import net.zomis.games.dsl.GameListener
import net.zomis.games.dsl.ReplayData
import net.zomis.games.dsl.impl.FlowStep

class ReplayingListener(private val data: ReplayData): GameListener {
    private var moves = 0

    override suspend fun handle(coroutineScope: CoroutineScope, step: FlowStep) {
        when (step) {
            is FlowStep.IllegalAction -> throw IllegalStateException("$step was not replayed correctly after $moves moves")
            is FlowStep.PreMove -> {
                step.state.clear()
                step.state.putAll(data.actions[moves++].state)
            }
            is FlowStep.PreSetup<*> -> {
                step.state.clear()
                step.state.putAll(data.initialState ?: emptyMap())
            }
            else -> {}
        }
    }
}