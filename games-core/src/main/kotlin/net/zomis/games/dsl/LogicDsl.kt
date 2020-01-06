package net.zomis.games.dsl

typealias ActionLogic2D<T, P> = Action2DScope<T, P>.() -> Unit
typealias ActionLogicInt<T> = ActionScope<T, Int>.() -> Unit
typealias ActionLogicSimple<T> = ActionScope<T, Unit>.() -> Unit

interface GameLogic<T : Any> {

    fun <P : Any> action2D(actionType: ActionType<Point>, grid: GridDsl<T, P>, logic: ActionLogic2D<T, P>)
    fun winner(function: (T) -> PlayerIndex)
    fun simpleAction(actionType: ActionType<Unit>, logic: ActionLogicSimple<T>)
    fun intAction(actionType: ActionType<Int>, options: Iterable<Int>, logic: ActionLogicInt<T>)

}

interface ReplayScope {
    fun state(key: String): Any
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

}