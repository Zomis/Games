package net.zomis.games.dsl

import kotlin.reflect.KClass

data class Point(val x: Int, val y: Int)
interface Actionable<T : Any, A : Any> {
    val playerIndex: Int
    val game: T
    val actionType: String
    val parameter: A
}
data class Action2D<T : Any, P : Any>(override val game: T, override val playerIndex: Int,
        override val actionType: String,
        val x: Int, val y: Int, val target: P): Actionable<T, Point> {
    override val parameter = Point(x, y)
}
data class Action<T : Any, A : Any>(override val game: T, override val playerIndex: Int,
        override val actionType: String,
        override val parameter: A): Actionable<T, A>

typealias PlayerIndex = Int?
fun PlayerIndex.isObserver(): Boolean = this == null

typealias GameSpec<T> = GameDsl<T>.() -> Unit
typealias GameModelDsl<T, C> = GameModel<T, C>.() -> Unit
typealias GameLogicDsl<T> = GameLogic<T>.() -> Unit
typealias GameViewDsl<T> = GameView<T>.() -> Unit
typealias GridDsl<T, P> = GameGrid<T, P>.() -> Unit

interface GameGrid<T, P> {
    val model: T
    fun size(sizeX: Int, sizeY: Int)
    fun getter(getter: (x: Int, y: Int) -> P)
}

interface GridSpec<T, P> {
    val sizeX: (T) -> Int
    val sizeY: (T) -> Int
    fun get(model: T, x: Int, y: Int): P
}

interface GameDsl<T : Any> {
    fun <P> gridSpec(spec: GridDsl<T, P>): GridDsl<T, P>
    fun <C : Any> setup(configClass: KClass<C>, modelDsl: GameModelDsl<T, C>)
    fun setup(modelDsl: GameModelDsl<T, Unit>)
    fun logic(logicDsl: GameLogicDsl<T>)
    fun view(viewDsl: GameViewDsl<T>)
}

fun <T : Any> createGame(name: String, dsl: GameDsl<T>.() -> Unit): GameSpec<T> {
    return dsl
}

data class ActionType<A : Any>(val name: String, val parameterType: KClass<A>)
fun <A : Any> createActionType(name: String, parameterType: KClass<A>): ActionType<A> {
    return ActionType(name, parameterType)
}
