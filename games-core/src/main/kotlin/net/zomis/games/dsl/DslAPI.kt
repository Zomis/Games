package net.zomis.games.dsl

import kotlin.reflect.KClass

data class Action2D<T, P>(val game: T, val playerIndex: Int, val x: Int, val y: Int, val target: P)

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
    fun logic(logicDsl: GameLogicDsl<T>)
    fun view(viewDsl: GameViewDsl<T>)
}

fun <T : Any> createGame(name: String, dsl: GameDsl<T>.() -> Unit): GameSpec<T> {
    return dsl
}
