package net.zomis.fights

import net.zomis.games.dsl.impl.GameAI

data class IntSummaryStatistics(
    val min: Int, val max: Int, val total: Int, val count: Int
) {
    operator fun plus(value: Int): IntSummaryStatistics = IntSummaryStatistics(
        min.coerceAtMost(value), max.coerceAtLeast(value), total + value, count + 1
    )
    operator fun plus(other: IntSummaryStatistics) = IntSummaryStatistics(
        min.coerceAtMost(other.min), max.coerceAtLeast(other.max), total + other.total, count + other.count
    )
}

interface MetricGroupingPlayerScope<T: Any> {
    val ai: GameAI<T>
    val playerIndex: Int
}
class MetricGroupingPlayerContext<T: Any>(private val fightSetup: FightSetup<T>, override val playerIndex: Int): MetricGroupingPlayerScope<T> {
    override val ai: GameAI<T> get() = fightSetup.players[playerIndex]
}
class IntermediateFightResult<T: Any, E>(
    val result: MutableMap<String, Any>,
    val values: Map<Any, List<MetricData<T, E>>>
) {
    fun displayCount(function: (E) -> Boolean) {
        result[this.toString()] = values.entries.associate {
            it.key to it.value.count { metricData -> function.invoke(metricData.data) }
        }
    }
}
fun <T: Any> IntermediateFightResult<T, Int>.displayIntStats() {
    result[this.toString()] = values.entries.associate {
        it.key to it.value.toIntSummaryStatistics()
    }
}

interface FightGroupingScope<T: Any> {
    fun displayIntStats(metric: FightMetric<T, Int>)
    fun <R> groupByAndTotal(metric: FightPlayerMetric<T, R>, groupBy: MetricGroupingPlayerScope<T>.() -> Any): IntermediateFightResult<T, R>
}

class FightResultsContext<T: Any>: FightGroupingScope<T> {
    private val result = mutableMapOf<String, Any>()

    fun results(): Any {
        return result.toMap()
    }

    override fun displayIntStats(metric: FightMetric<T, Int>) {
        this.result[metric.toString()] = metric.values.toIntSummaryStatistics()
    }

    override fun <R> groupByAndTotal(
        metric: FightPlayerMetric<T, R>,
        groupBy: MetricGroupingPlayerScope<T>.() -> Any
    ): IntermediateFightResult<T, R> {
        /*
        * Points    1 15, 3 16, 5 17, 7 18, 9 19
        * -->
        * AI_A: 1, 3, 5, 7, 9
        * AI_B: 15, 16, 17, 18, 19
        *
        * Other future keys: playerIndex? value itself? length of game?
        */
        val categorization = mutableMapOf<Any, MutableList<MetricData<T, R>>>()

        metric.values.forEach { data ->
            // loop through games and players and put in the appropriate categorization
            for (i in data.fight.players.indices) {
                val groupContext = MetricGroupingPlayerContext(data.fight, i)
                val category = groupBy.invoke(groupContext)
                categorization.getOrPut(category) { mutableListOf() }.add(MetricData(data.fight, data.data[i]))
            }
        }
        return IntermediateFightResult<T, R>(this.result, categorization)
    }

}

private fun List<MetricData<*, Int>>.toIntSummaryStatistics(): IntSummaryStatistics {
    if (this.isEmpty()) return IntSummaryStatistics(Int.MAX_VALUE, Int.MIN_VALUE, 0, 0)
    val first = this.first()
    val result = this.toList().fold(IntSummaryStatistics(first.data, first.data, 0, 0)) { acc, metricData ->
        acc + metricData.data
    }
    return result
}
