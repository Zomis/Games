package net.zomis.games.dsl

import net.zomis.games.common.PlayerIndex

typealias ViewDsl2D<T, P> = GameView2D<T, P>.() -> Unit

typealias GameViewOnRequestFunction<T> = GameViewOnRequestScope<T>.(request: Map<String, Any>) -> Any
interface GameViewOnRequestScope<T> {
    val viewer: PlayerIndex
    val game: T
}

interface Viewable {
    fun toView(viewer: PlayerIndex): Any?
}

interface ViewScope<T> {
    val game: T
    val viewer: PlayerIndex
}

interface GameView<T> : ViewScope<T> {
    fun result(): Map<String, Any?>
    fun currentPlayer(function: (T) -> Int)
    fun <P> grid(name: String, grid: GridDsl<T, P>, view: ViewDsl2D<T, P>)
    fun value(key: String, value: (T) -> Any?)
    fun eliminations()
    fun onRequest(requestName: String, function: GameViewOnRequestFunction<T>)
}

interface GameView2D<T, P> {
    val model: T
    fun owner(function: (tile: P) -> Int?)
    fun property(name: String, value: (tile: P) -> Any?)
}
