package net.zomis.games.listeners

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import net.zomis.games.dsl.GameListener
import net.zomis.games.dsl.impl.FlowStep

class SteppingGameListener: GameListener {

    private val internalChannel = Channel<FlowStep>()

    override suspend fun handle(coroutineScope: CoroutineScope, step: FlowStep) {
        internalChannel.send(step)
    }

    suspend fun takeUntil(function: (FlowStep) -> Boolean): FlowStep {
        while (true) {
            val output = next()
            if (function.invoke(output)) return output
        }
    }

    suspend fun next(): FlowStep = internalChannel.receive()

}