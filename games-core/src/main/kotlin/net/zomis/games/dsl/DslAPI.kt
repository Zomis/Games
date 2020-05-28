package net.zomis.games.dsl

import net.zomis.games.PlayerEliminations
import kotlin.math.absoluteValue
import kotlin.math.sqrt
import kotlin.reflect.KClass

data class Point(val x: Int, val y: Int) {
    fun minus(other: Point): Point {
        return Point(x - other.x, y - other.y)
    }

    fun abs(): Point = Point(this.x.absoluteValue, this.y.absoluteValue)
    fun distance(): Double = sqrt(this.x.toDouble() * this.x + this.y.toDouble() * this.y)
}

data class PointMove(val source: Point, val destination: Point)
interface Actionable<T : Any, A : Any> {
    val playerIndex: Int
    val game: T
    val actionType: String
    val parameter: A
}
interface GameUtils {
    val playerEliminations: PlayerEliminations
    val replayable: ReplayableScope
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
typealias GameRulesDsl<T> = GameRules<T>.() -> Unit
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
    fun rules(rulesDsl: GameRulesDsl<T>)
}

fun <T : Any> createGame(name: String, dsl: GameDsl<T>.() -> Unit): GameSpec<T> {
    return dsl
}

data class ActionSerialization<A : Any, T : Any>(val serialize: (A) -> Any, val deserialize: ActionOptionsScope<T>.(Any) -> A)
data class ActionType<A : Any>(val name: String, val parameterType: KClass<A>, val serialize: ActionSerialization<A, Any>)
fun <A : Any> createActionType(name: String, parameterType: KClass<A>): ActionType<A> {
    return ActionType(name, parameterType, ActionSerialization({it}, {it as A}))
}
fun <A : Any, T : Any> createActionType(name: String, parameterType: KClass<A>, serialize: ActionSerialization<A, T>): ActionType<A> {
    return ActionType(name, parameterType, serialize as ActionSerialization<A, Any>)
}
