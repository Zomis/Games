package net.zomis.fights

import kotlinx.coroutines.CoroutineScope
import net.zomis.games.dsl.GameListener
import net.zomis.games.dsl.impl.FlowStep
import net.zomis.games.dsl.impl.Game

class MetricData<T: Any, E>(val fight: FightSetup<T>, val data: E)

class FightMetric<T: Any, E>(val block: MetricDsl<T, E>) {
    internal val values = mutableListOf<MetricData<T, E>>()
}
class FightPlayerMetric<T: Any, E>(val block: MetricPlayerDsl<T, E>) {
    internal val values = mutableListOf<MetricData<T, List<E>>>()
}

class MetricsListener<T: Any> {

    val endGameMetrics = mutableListOf<FightMetric<T, Any>>()
    val endGamePlayerMetrics = mutableListOf<FightPlayerMetric<T, Any>>()

    fun fight(fightSetup: FightSetup<T>, game: Game<T>): GameListener {
        return object : GameListener {
            override suspend fun handle(coroutineScope: CoroutineScope, step: FlowStep) {
                if (step is FlowStep.PreMove) {
                }
                if (step is FlowStep.ActionPerformed<*>) {

                }
                if (step is FlowStep.GameEnd) {
                    endGameMetrics.forEach {
                        val data = it.block.invoke(MetricGameContext(game.model, game.eliminations))
                        it.values.add(MetricData(fightSetup, data))
                    }
                    endGamePlayerMetrics.forEach {
                        val data = game.playerIndices.map { playerIndex ->
                            it.block.invoke(MetricGamePlayerContext(game.model, game.eliminations, playerIndex))
                        }
                        it.values.add(MetricData(fightSetup, data))
                    }
                }
            }
        }
    }

    fun <E> endGameMetric(block: MetricGameScope<T>.() -> E): FightMetric<T, E> {
        return FightMetric(block).also { this.endGameMetrics.add(it as FightMetric<T, Any>) }
    }

    fun produceResults(function: FightGroupingScope<T>.() -> Unit): Any {
        endGameMetrics.forEach {
            println(it)
            it.values.forEach { data -> println("${data.fight}: ${data.data}") }
            println()
        }

        val context = FightResultsContext<T>()
        function.invoke(context)
        return context.results()
    }

    fun <E> endGamePlayerMetric(block: MetricPlayerDsl<T, E>): FightPlayerMetric<T, E> {
        return FightPlayerMetric(block).also { this.endGamePlayerMetrics.add(it as FightPlayerMetric<T, Any>) }
    }

}