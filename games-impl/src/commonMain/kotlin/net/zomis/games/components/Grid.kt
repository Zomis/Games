package net.zomis.games.components

import net.zomis.games.common.Point
import net.zomis.games.common.Rect
import net.zomis.games.common.fmod

// Artax, TTT, Othello...
// rules.grid.move/place

class GridPointImpl<T>(
    override val x: Int, override val y: Int,
    private val getter: (x: Int, y: Int) -> T?,
    private val setter: (x: Int, y: Int, value: T) -> Unit
): GridPoint<T> {
    override var value: T
        get() = getter(x, y) ?: throw IllegalStateException("Position $this does not have a value")
        set(value) { setter(x, y, value) }

    override fun valueOrNull(): T? = getter(x, y)

    override fun rangeCheck(map: Grid<T>): GridPoint<T>? = this.takeIf { map.isOnMap(x, y) }
    override fun toString(): String = "Map2DPointImpl($x, $y)"
}

interface GridPoint<T> {
    val x: Int
    val y: Int
    var value: T
    fun rangeCheck(map: Grid<T>): GridPoint<T>?
    fun valueOrNull(): T?

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
    fun point(x: Int, y: Int): GridPoint<T> = GridPointImpl(x, y, this::getOrNull, this::set)
    fun point(point: Point): GridPoint<T> = point(point.x, point.y)
    fun isOnMap(x: Int, y: Int): Boolean {
        val border = border()
        return x >= border.left && y >= border.top && x <= border.right && y <= border.bottom
    }

    fun wrapAround(point: Point): Point = Point(point.x fmod sizeX, point.y fmod sizeY)
    fun border(): Rect = Rect(top = 0, left = 0, right = sizeX - 1, bottom = sizeY - 1)
    fun points(): Iterable<Point> {
        val border = border()
        return (border.top..border.bottom).flatMap { y ->
            (border.left..border.right).map { x -> Point(x, y) }
        }
    }
    fun all(): Iterable<GridPoint<T>> = points().map { point(it.x, it.y) }

    fun view(viewFunction: (T) -> Any?): Map<String, Any> {
        val border = border()
        return mapOf(
            "left" to border.left,
            "top" to border.top,
            "width" to border.width(),
            "height" to border.height(),
            "grid" to boardView(viewFunction)
        )
    }
    fun boardView(viewFunction: (T) -> Any?): List<List<Any?>> {
        val border = border()
        return (border.top..border.bottom).map { y ->
            (border.left..border.right).map { x ->
                val pos = getOrNull(x, y)
                if (pos != null) viewFunction(pos) else null
            }
        }
    }
}

class GridImpl<T>(override val sizeX: Int, override val sizeY: Int, val factory: (x: Int, y: Int) -> T) : Grid<T> {
    private val border = Rect(0, 0, sizeX - 1, sizeY - 1)
    val grid: MutableList<MutableList<T>> = (0 until sizeY).map { y ->
        (0 until sizeX).map { x ->
            factory(x, y)
        }.toMutableList()
    }.toMutableList()

    override fun border(): Rect = border
    override fun set(x: Int, y: Int, value: T) { grid[y][x] = value }
    override fun get(x: Int, y: Int): T = grid[y][x]

}

class GridSubView<T>(private val original: Grid<T>, val origin: Point, override val sizeX: Int, override val sizeY: Int): Grid<T> {
    override fun set(x: Int, y: Int, value: T) {
        require(this.isOnMap(x, y))
        return original.set(x + origin.x, y + origin.y, value)
    }

    override fun get(x: Int, y: Int): T {
        require(this.isOnMap(x, y))
        return original.get(x + origin.x, y + origin.y)
    }
}

fun <T> Grid<T>.subGrid(x: Int, y: Int, subSizeX: Int, subSizeY: Int): Grid<T>
    = GridSubView(this, Point(x, y), subSizeX, subSizeY)
