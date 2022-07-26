package net.zomis.games.dsl.listeners

import kotlinx.coroutines.CoroutineScope
import net.zomis.games.dsl.GameListener
import net.zomis.games.dsl.impl.FlowStep

class IllegalActionListener(val exception: Boolean): GameListener {
    override suspend fun handle(coroutineScope: CoroutineScope, step: FlowStep) {
        if (step is FlowStep.IllegalAction) {
            if (exception) throw IllegalStateException("IllegalAction was made: $step")
            println("Illegal Action was made: $step in $this")
        }
    }
}
