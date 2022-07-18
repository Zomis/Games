package net.zomis.games.dsl.listeners

import kotlinx.coroutines.CoroutineScope
import net.zomis.games.dsl.GameListener
import net.zomis.games.dsl.impl.FlowStep

class CombinedListener(vararg listeners: GameListener): GameListener {
    private val listeners = listeners.toList()

    override suspend fun handle(coroutineScope: CoroutineScope, step: FlowStep) {
        for (listener in listeners) {
            listener.handle(coroutineScope, step)
        }
    }
}
