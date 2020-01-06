package net.zomis.games.dsl

typealias ActionLogic2D<T, P> = Action2DScope<T, P>.() -> Unit
typealias ActionLogicInt<T> = ActionScope<T, Int>.() -> Unit
typealias ActionLogicSingleTarget<T, A> = ActionScope<T, A>.() -> Unit
typealias ActionLogicAdvanced<T, A> = ActionComplexScope<T, A>.() -> Unit
typealias ActionLogicSimple<T> = ActionScope<T, Unit>.() -> Unit

interface GameLogic<T : Any> {
    fun <P : Any> action2D(actionType: ActionType<Point>, grid: GridDsl<T, P>, logic: ActionLogic2D<T, P>)
    fun winner(function: (T) -> PlayerIndex)
    fun simpleAction(actionType: ActionType<Unit>, logic: ActionLogicSimple<T>)
    fun intAction(actionType: ActionType<Int>, options: (T) -> Iterable<Int>, logic: ActionLogicInt<T>)
    fun <A : Any> singleTarget(actionType: ActionType<A>, options: (T) -> Iterable<A>, logic: ActionLogicSingleTarget<T, A>)
    fun <A : Any> action(actionType: ActionType<A>, logic: ActionLogicAdvanced<T, A>)
}

interface ReplayScope {
    fun state(key: String): Any
    fun fullState(key: String): Any?
}
interface EffectScope {
    fun state(key: String, value: Any)
}

interface Action2DScope<T : Any, P : Any> {

    fun allowed(condition: (Action2D<T, P>) -> Boolean)
    fun effect(effect: EffectScope.(Action2D<T, P>) -> Unit)

}

interface ActionScope<T : Any, P : Any> {

    fun allowed(condition: (Action<T, P>) -> Boolean)
    fun effect(effect: EffectScope.(Action<T, P>) -> Unit)
    fun replayEffect(effect: ReplayScope.(Action<T, P>) -> Unit)

}

interface ActionComplexScopeResultNext<T : Any, A : Any> : ActionComplexScopeResultStart<T, A> {
    fun actionParameter(action: A)
}
interface ActionComplexScopeResultStart<T : Any, A : Any> {
    fun <E : Any> option(options: Array<E>, next: ActionComplexScopeResultNext<T, A>.(E) -> Unit)
}

interface ActionComplexScope<T : Any, A : Any> {

    fun options(options: ActionComplexScopeResultStart<T, A>.() -> Unit)
    fun allowed(condition: (Action<T, A>) -> Boolean)
    fun effect(effect: EffectScope.(Action<T, A>) -> Unit)
    fun replayEffect(effect: ReplayScope.(Action<T, A>) -> Unit)

}