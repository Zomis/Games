package net.zomis.games.listeners

import kotlinx.coroutines.CoroutineScope
import net.zomis.games.dsl.GameListener
import net.zomis.games.dsl.impl.FlowStep

class MaxMoves(private val limit: Int): GameListener {
    private var count = 0

    override suspend fun handle(coroutineScope: CoroutineScope, step: FlowStep) {
        if (step is FlowStep.ActionPerformed<*>) count++
        if (count > limit) throw IllegalStateException("Exceeded MaxMoves: $count (limit was $limit)")
    }
}
