package net.zomis.games.listeners

import kotlinx.coroutines.CoroutineScope
import net.zomis.games.dsl.GameListener
import net.zomis.games.dsl.impl.FlowStep

class LimitedNextViews(private val limit: Int): GameListener {
    private var count = 0

    override suspend fun handle(coroutineScope: CoroutineScope, step: FlowStep) {
        if (step is FlowStep.NextView) count++
        if (step is FlowStep.AwaitInput) count = 0
        if (count > limit) throw IllegalStateException("Exceeded NextView limit: $count (limit is $limit)")
    }
}
