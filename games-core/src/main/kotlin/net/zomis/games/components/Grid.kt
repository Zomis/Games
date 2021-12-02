package net.zomis.games.components

import net.zomis.games.common.Point
import net.zomis.games.common.fmod

// Artax, TTT, Othello...
// rules.grid.move/place

class GridPointImpl<T>(
    override val x: Int, override val y: Int,
    private val getter: (x: Int, y: Int) -> T,
    private val setter: (x: Int, y: Int, value: T) -> Unit
): GridPoint<T> {
    override var value: T
        get() = getter(x, y)
        set(value) { setter(x, y, value) }

    override fun rangeCheck(map: Grid<T>): GridPoint<T>?
        = this.takeUnless { x < 0 || x >= map.sizeX || y < 0 || y >= map.sizeY }
    override fun toString(): String = "Map2DPointImpl($x, $y)"
}

interface GridPoint<T> {
    val x: Int
    val y: Int
    var value: T
    fun rangeCheck(map: Grid<T>): GridPoint<T>?
    val point get() = Point(x, y)
}

class GridLine<T>(val items: List<T>) {
    fun <R> hasConsecutive(k: Int, mapping: (T) -> R?): R? {
        var result: R? = null
        var consecutive = 0

        for (item in items) {
            val current = mapping(item)
            if (current == result) {
                consecutive++
            } else {
                consecutive = 1
                result = current
            }

            if (consecutive >= k && result != null) {
                return result
            }
        }
        return null
    }
}

interface Grid<T> {
    val sizeX: Int
    val sizeY: Int
    fun set(x: Int, y: Int, value: T)
    fun get(x: Int, y: Int): T
    fun getOrNull(point: Point): T? = getOrNull(point.x, point.y)
    fun getOrNull(x: Int, y: Int): T? = if (isOnMap(x, y)) get(x, y) else null
    fun point(x: Int, y: Int): GridPoint<T> = GridPointImpl(x, y, this::get, this::set)
    fun point(point: Point): GridPoint<T> = point(point.x, point.y)
    fun isOnMap(x: Int, y: Int): Boolean = x >= 0 && y >= 0 && x < this.sizeX && y < this.sizeY
    fun wrapAround(point: Point): Point = Point(point.x fmod sizeX, point.y fmod sizeY)
    fun points(): Iterable<Point> = (0 until sizeY).flatMap { y ->
        (0 until sizeX).map { x -> Point(x, y) }
    }
    fun all(): Iterable<GridPoint<T>> = points().map { point(it.x, it.y) }

    fun view(viewFunction: (T) -> Any?): Any = mapOf(
        "width" to sizeX,
        "height" to sizeY,
        "grid" to boardView(viewFunction)
    )
    fun boardView(viewFunction: (T) -> Any?): Any
        = (0 until sizeY).map { y -> (0 until sizeX).map { viewFunction(get(it, y)) } }
}

class GridImpl<T>(override val sizeX: Int, override val sizeY: Int, val factory: (x: Int, y: Int) -> T) : Grid<T> {
    val grid: MutableList<MutableList<T>> = (0 until sizeY).map { y ->
        (0 until sizeX).map { x ->
            factory(x, y)
        }.toMutableList()
    }.toMutableList()

    override fun set(x: Int, y: Int, value: T) { grid[y][x] = value }
    override fun get(x: Int, y: Int): T = grid[y][x]

}
