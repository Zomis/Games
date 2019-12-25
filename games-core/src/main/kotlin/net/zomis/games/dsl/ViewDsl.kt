package net.zomis.games.dsl

typealias ViewDsl2D<T, P> = GameView2D<T, P>.() -> Unit

interface GameView<T> {
    fun result(): Map<String, Any?>
    fun currentPlayer(function: (T) -> Int)
    fun <P> grid(name: String, view: ViewDsl2D<T, P>)
    fun winner(function: (T) -> Int?)
}

interface GameView2D<T, P> {
    val model: T
    fun size(sizeX: Int, sizeY: Int)
    fun getter(getter: (x: Int, y: Int) -> P)
    fun owner(function: (tile: P) -> Int?)
}
