package net.zomis.games.listeners

import kotlinx.coroutines.CoroutineScope
import net.zomis.games.dsl.GameListener
import net.zomis.games.dsl.impl.FlowStep

class NoOpListener: GameListener {
    override suspend fun handle(coroutineScope: CoroutineScope, step: FlowStep) {}
}
