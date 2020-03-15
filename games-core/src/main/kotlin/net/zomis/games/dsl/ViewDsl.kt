package net.zomis.games.dsl

typealias ViewDsl2D<T, P> = GameView2D<T, P>.() -> Unit

interface GameView<T> {
    val viewer: PlayerIndex
    fun result(): Map<String, Any?>
    fun currentPlayer(function: (T) -> Int)
    fun <P> grid(name: String, grid: GridDsl<T, P>, view: ViewDsl2D<T, P>)
    fun winner(function: (T) -> Int?)
    fun value(key: String, value: (T) -> Any?)
    fun state(key: String, function: ReplayScope.(T) -> Any?)
    fun eliminations()
}

interface GameView2D<T, P> {
    val model: T
    fun owner(function: (tile: P) -> Int?)
    fun property(name: String, value: (tile: P) -> Any?)
}
