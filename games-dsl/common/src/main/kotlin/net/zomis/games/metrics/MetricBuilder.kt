package net.zomis.games.metrics

import net.zomis.games.PlayerEliminationsRead
import net.zomis.games.dsl.ActionType
import net.zomis.games.dsl.Actionable

typealias MetricDsl<T, E> = MetricGameScope<T>.() -> E
typealias MetricPlayerDsl<T, E> = MetricPlayerScope<T>.() -> E
typealias MetricActionDsl<T, A, E> = MetricActionScope<T, A>.() -> E

interface MetricGameScope<T: Any> {
    val game: T
    val eliminations: PlayerEliminationsRead
}
interface MetricPlayerScope<T: Any>: MetricGameScope<T> {
    val playerIndex: Int
}
interface MetricActionScope<T: Any, A: Any>: MetricPlayerScope<T> {
    val action: Actionable<T, A>
}

interface MetricBuilder<T: Any> {
    fun <A: Any, E> actionMetric(actionType: ActionType<T, A>, block: MetricActionScope<T, A>.() -> E): FightActionMetric<T, A, E>
    fun <E> endGameMetric(block: MetricGameScope<T>.() -> E): FightMetric<T, E>
    fun <E> endGamePlayerMetric(block: MetricPlayerScope<T>.() -> E): FightPlayerMetric<T, E>
}
