package net.zomis.games.dsl.listeners

import kotlinx.coroutines.CoroutineScope
import net.zomis.games.dsl.GameListener
import net.zomis.games.dsl.impl.ActionLogEntry
import net.zomis.games.dsl.impl.FlowStep

class LogKeeper: GameListener {
    private val logData = mutableListOf<ActionLogEntry>()
    val logs get() = logData.toList()

    override suspend fun handle(coroutineScope: CoroutineScope, step: FlowStep) {
        if (step is FlowStep.Log) {
            logData.add(step.log)
        }
    }

    fun clearLogs() = this.logData.clear()
}
