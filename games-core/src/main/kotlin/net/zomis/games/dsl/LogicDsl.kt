package net.zomis.games.dsl

typealias ActionLogic2D<T, P> = GameLogic2D<T, P>.() -> Unit

interface GameLogic<T : Any> {

    fun <P : Any> action2D(actionType: ActionType<Point>, grid: GridDsl<T, P>, logic: ActionLogic2D<T, P>)
    fun winner(function: (T) -> PlayerIndex)

}

interface GameLogic2D<T : Any, P : Any> {

    fun allowed(condition: (Action2D<T, P>) -> Boolean)
    fun effect(effect: (Action2D<T, P>) -> Unit)

}