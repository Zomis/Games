package net.zomis.games.dsl.flow

import net.zomis.games.dsl.impl.FlowStep

class GameFlowTestHelper<T: Any>(val gameFlow: GameFlowImpl<T>) {
    suspend fun takeUntil(condition: (FlowStep) -> Boolean): FlowStep {
        while (true) {
            val output = next()
            if (condition.invoke(output)) {
                return output
            }
        }
    }

    suspend fun next(): FlowStep {
        val output2 = gameFlow.feedbackFlow.receive()
        println("Test Received: $output2")
        return output2
    }
}
