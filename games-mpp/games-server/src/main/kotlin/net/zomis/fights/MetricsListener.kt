package net.zomis.fights

import kotlinx.coroutines.CoroutineScope
import net.zomis.games.dsl.ActionType
import net.zomis.games.dsl.Actionable
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
class FightActionMetric<T: Any, A: Any, E>(val actionType: ActionType<T, A>, val block: MetricActionDsl<T, A, E>) {
    internal val byPlayer = mutableMapOf<Int, MutableList<MetricData<T, E>>>()
}

class MetricsListener<T: Any> {

    val actionMetrics = mutableListOf<FightActionMetric<T, Any, Any>>()
    val endGameMetrics = mutableListOf<FightMetric<T, Any>>()
    val endGamePlayerMetrics = mutableListOf<FightPlayerMetric<T, Any>>()

    fun fight(fightSetup: FightSetup<T>, game: Game<T>): GameListener {
        return object : GameListener {
            override suspend fun handle(coroutineScope: CoroutineScope, step: FlowStep) {
                if (step is FlowStep.PreMove) {
                }
                if (step is FlowStep.ActionPerformed<*>) {
                    actionMetrics.filter { it.actionType.name == step.action.actionType }.forEach {
                        val context = MetricActionContext(game.model, game.eliminations, step.action as Actionable<T, Any>)
                        val data = it.block.invoke(context)
                        it.byPlayer.getOrPut(step.playerIndex) { mutableListOf() }.add(MetricData(fightSetup, data))
                    }
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

    fun produceResults(function: FightGroupingScope<T>.() -> Unit): Map<String, Any> {
        val context = FightResultsContext<T>()
        function.invoke(context)
        return context.results()
    }

    fun <A: Any, E> actionMetric(actionType: ActionType<T, A>, block: MetricActionScope<T, A>.() -> E): FightActionMetric<T, A, E> {
        return FightActionMetric(actionType, block).also { this.actionMetrics.add(it as FightActionMetric<T, Any, Any>) }
    }

    fun <E> endGamePlayerMetric(block: MetricPlayerDsl<T, E>): FightPlayerMetric<T, E> {
        return FightPlayerMetric(block).also { this.endGamePlayerMetrics.add(it as FightPlayerMetric<T, Any>) }
    }

}