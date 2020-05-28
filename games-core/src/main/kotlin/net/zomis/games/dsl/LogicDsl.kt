package net.zomis.games.dsl

import net.zomis.games.PlayerEliminations

typealias ActionLogic<T, P, A> = ActionScope<T, P, A>.() -> Unit
typealias ActionLogicAdvanced<T, A> = ActionComplexScope<T, A>.() -> Unit

interface GameLogic<T : Any> {
    fun <P : Any> action2D(actionType: ActionType<Point>, grid: GridDsl<T, P>, logic: ActionLogic<T, Point, Action2D<T, P>>)
    fun winner(function: (T) -> PlayerIndex)
    fun simpleAction(actionType: ActionType<Unit>, logic: ActionLogic<T, Unit, Action<T, Unit>>)
    fun intAction(actionType: ActionType<Int>, options: (T) -> Iterable<Int>, logic: ActionLogic<T, Int, Action<T, Int>>)
    fun <A : Any> singleTarget(actionType: ActionType<A>, options: (T) -> Iterable<A>, logic: ActionLogic<T, A, Action<T, A>>)
    fun <A : Any> action(actionType: ActionType<A>, logic: ActionLogicAdvanced<T, A>)
}

interface ReplayableScope {
    fun map(key: String, default: () -> Map<String, Any>): Map<String, Any>
    fun int(key: String, default: () -> Int): Int
    fun ints(key: String, default: () -> List<Int>): List<Int>
    fun string(key: String, default: () -> String): String
    fun strings(key: String, default: () -> List<String>): List<String>
    fun list(key: String, default: () -> List<Map<String, Any>>): List<Map<String, Any>>
}
@Deprecated("Use ReplayableScope instead")
interface ReplayScope {
    fun state(key: String): Any
    fun fullState(key: String): Any?
}
interface EffectScope : GameUtils {
    override val playerEliminations: PlayerEliminations
    override val replayable: ReplayableScope
    fun state(key: String, value: Any)
}

interface ActionScope<T : Any, P : Any, A : Actionable<T, P>> {

    fun allowed(condition: (A) -> Boolean)
    fun effect(effect: EffectScope.(A) -> Unit)
    fun replayEffect(effect: ReplayScope.(A) -> Unit)

}

interface ActionComplexScopeResultNext<T : Any, A : Any> : ActionComplexScopeResultStart<T, A> {
    fun actionParameter(action: A)
}
interface ActionComplexScopeResultStart<T : Any, A : Any> {
    fun <E : Any> optionFrom(options: (T) -> Iterable<E>, next: ActionComplexScopeResultNext<T, A>.(E) -> Unit)
    fun <E : Any> option(options: Iterable<E>, next: ActionComplexScopeResultNext<T, A>.(E) -> Unit)
}

interface ActionComplexScope<T : Any, A : Any> {

    fun options(options: ActionComplexScopeResultStart<T, A>.() -> Unit)
    fun allowed(condition: (Action<T, A>) -> Boolean)
    fun effect(effect: EffectScope.(Action<T, A>) -> Unit)
    fun replayEffect(effect: ReplayScope.(Action<T, A>) -> Unit)

}