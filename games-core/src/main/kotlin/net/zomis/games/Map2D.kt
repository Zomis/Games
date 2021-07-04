package net.zomis.games

import net.zomis.Best
import net.zomis.games.common.Point

private fun flipX(start: Position): Position {
    return Position(start.sizeX - 1 - start.x, start.y, start.sizeX, start.sizeY)
}
private fun flipY(start: Position): Position {
    return Position(start.x, start.sizeY - 1 - start.y, start.sizeX, start.sizeY)
}
private fun rotate(start: Position): Position {
    return Position(start.sizeY - 1 - start.y, start.x, start.sizeY, start.sizeX)
}

data class Position(val x: Int, val y: Int, val sizeX: Int, val sizeY: Int) {
    fun next(): Position? {
        if (x == sizeX - 1) {
            return if (y == sizeY - 1) null else Position(0, y + 1, sizeX, sizeY)
        }
        return Position(this.x + 1, this.y, this.sizeX, this.sizeY)
    }

    fun transform(transformation: Transformation): Position {
        return transformation.transform(this)
    }
}

enum class TransformationType(val transforming: (Position) -> Position, val reverse: (Position) -> Position) {
    FLIP_X(::flipX, ::flipX),
    FLIP_Y(::flipY, ::flipY),
    ROTATE(::rotate, { start -> rotate(rotate(rotate(start))) }),
    ;
}

enum class Transformation(private val transformations: List<TransformationType>) {
    NO_CHANGE(listOf()),
    FLIP_X(listOf(TransformationType.FLIP_X)),
    FLIP_Y(listOf(TransformationType.FLIP_Y)),
    ROTATE_90(listOf(TransformationType.ROTATE)),
    ROTATE_180(listOf(TransformationType.ROTATE, TransformationType.ROTATE)),
    ROTATE_270(listOf(TransformationType.ROTATE, TransformationType.ROTATE, TransformationType.ROTATE)),
    ROTATE_90_FLIP_X(listOf(TransformationType.ROTATE, TransformationType.FLIP_X)),
    ROTATE_90_FLIP_Y(listOf(TransformationType.ROTATE, TransformationType.FLIP_Y)),
    ;

    fun transform(position: Position): Position {
        return transformations.fold(position) { pos, trans -> trans.transforming(pos) }
    }

    fun reverseTransform(position: Position): Position {
        return transformations.reversed().fold(position) { pos, trans -> trans.reverse(pos) }
    }

    private val referencePoints = arrayOf(
        Position(3, 2, 5, 5),
        Position(4, 0, 5, 5)
    )
    fun apply(transformation: Transformation): Transformation {
        val simplestTransformation = Transformation.values().filter {result ->
            referencePoints.all {p -> transformation.transform(this.transform(p)) == result.transform(p) }
        }
        return simplestTransformation.single()
    }

}

class Map2D<T>(val sizeX: Int, val sizeY: Int, val getter: (x: Int, y: Int) -> T, val setter: (x: Int, y: Int, value: T) -> Unit = {_,_,_->}) {

    private fun originalPossibleTransformations(): MutableSet<Transformation> {
        val possibleTransformations = Transformation.values().toMutableSet()
        if (sizeX != sizeY) {
            // Rotating 90 or 270 degrees only works if both width or height is the same
            possibleTransformations.remove(Transformation.ROTATE_90)
            possibleTransformations.remove(Transformation.ROTATE_270)
        }
        return possibleTransformations
    }

    fun standardizedTransformation(valueFunction: (T) -> Int): Transformation {
        // keep a Set<Transformation>, start with all of them
        // loop through Transformations and find ones with the extremes
        // the goal is that of all the possible transformations, the result should be the one with the lowest/highest value

        // start in the possible fields for the target map upper-left corner
        // then continue, line by line, beginning with increasing X and then increase Y
        val possibleTransformations = originalPossibleTransformations()

        var position: Position? = Position(0, 0, sizeX, sizeY)
        while (possibleTransformations.size > 1 && position != null) {
            val best = Best<Transformation> { transformation ->
                val originalPos = transformation.reverseTransform(position!!)
                val originalT = getter(originalPos.x, originalPos.y)
                valueFunction(originalT).toDouble()
            }
            possibleTransformations.forEach {
                best.next(it)
            }
            possibleTransformations.retainAll(best.getBest())

            position = position.next()
        }

        val transformation = possibleTransformations.first() // map can be symmetric so don't use .single
        return transformation
    }

    fun symmetryTransformations(equalsFunction: (T, T) -> Boolean): Set<Transformation> {
        val possibleTransformations = originalPossibleTransformations()
        return possibleTransformations.filter { transformation ->
            positions().all { pos ->
                val other = transformation.transform(pos)
                equalsFunction(getter(pos.x, pos.y), getter(other.x, other.y))
            }
        }.toSet()
    }

    fun transform(transformation: Transformation) {
        val rotated = Map2DX(sizeX, sizeY) { x, y ->
            val pos = Position(x, y, sizeX, sizeY)
            val oldPos = transformation.reverseTransform(pos)
            getter(oldPos.x, oldPos.y)
        }

        (0 until sizeY).forEach {y ->
            (0 until sizeX).forEach {x ->
                setter(x, y, rotated.grid[y][x])
            }
        }
    }

    fun positions(): Sequence<Position> {
        return (0 until sizeY).asSequence().flatMap { y ->
            (0 until sizeX).asSequence().map { x ->
                Position(x, y, sizeX, sizeY)
            }
        }
    }

    fun standardize(valueFunction: (T) -> Int) {
        this.transform(this.standardizedTransformation(valueFunction))
    }

}

interface Map2DPoint<T> {
    val x: Int
    val y: Int
    var value: T
    fun rangeCheck(map: Map2DX<T>): Map2DPoint<T>?
}
class Map2DPointImpl<T>(
    override val x: Int, override val y: Int,
    private val getter: (x: Int, y: Int) -> T,
    private val setter: (x: Int, y: Int, value: T) -> Unit
): Map2DPoint<T> {
    override var value: T
        get() = getter(x, y)
        set(value) { setter(x, y, value) }

    override fun rangeCheck(map: Map2DX<T>): Map2DPoint<T>? {
        return this.takeUnless { x < 0 || x >= map.sizeX || y < 0 || y >= map.sizeY }
    }

    override fun toString(): String = "Map2DPointImpl($x, $y)"
}
class Map2DX<T>(val sizeX: Int, val sizeY: Int, val factory: (x: Int, y: Int) -> T) {

    val grid: MutableList<MutableList<T>> = (0 until sizeY).map { y ->
        (0 until sizeX).map { x ->
            factory(x, y)
        }.toMutableList()
    }.toMutableList()

    fun set(x: Int, y: Int, value: T) {
        grid[y][x] = value
    }

    fun get(x: Int, y: Int): T = grid[y][x]
    fun getOrNull(x: Int, y: Int): T? {
        return if (x in 0 until sizeX && y in 0 until sizeY) get(x, y) else null
    }

    fun point(x: Int, y: Int): Map2DPoint<T> {
        return Map2DPointImpl(x, y, this::get, this::set)
    }

    fun point(point: Point): Map2DPoint<T> {
        return point(point.x, point.y)
    }

    fun points(): Iterable<Point> = grid.indices.flatMap { y ->
        grid[y].indices.map { x -> Point(x, y) }
    }

    fun all(): Iterable<Map2DPoint<T>> = points().map { point(it.x, it.y) }

    fun asMap2D(): Map2D<T> {
        return Map2D(sizeX, sizeY, { x, y -> grid[y][x] }) {x, y, v ->
            grid[y][x] = v
        }
    }

    fun standardize(value: (T) -> Int): Map2DX<T> {
        asMap2D().standardize(value)
        return this
    }

    override fun toString(): String {
        return "Map2D(grid=$grid)"
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
    fun mnkLines(includeDiagonals: Boolean): Sequence<GridLine<T>> {
        // val points = if (includeDiagonals) Direction8.values().map { it.delta() } else Direction4.values().map { it.delta() }

        fun loopAdd(board: Map2DX<T>, xxStart: Int, yyStart: Int, dx: Int, dy: Int): List<T> {
            var xx = xxStart
            var yy = yyStart
            val items = mutableListOf<T>()

            var tile: T?
            do {
                tile = board.getOrNull(xx, yy)
                xx += dx
                yy += dy
                if (tile != null) {
                    items.add(tile)
                }
            } while (tile != null)

            return items
        }

        return sequence {
            // columns
            for (xx in 0 until sizeX) {
                yield(GridLine(loopAdd(this@Map2DX, xx, 0, 0, 1)))
            }

            // Scan rows for a winner
            for (yy in 0 until sizeY) {
                yield(GridLine(loopAdd(this@Map2DX, 0, yy, 1, 0)))
            }

            if (!includeDiagonals) {
                return@sequence
            }

            // Scan diagonals for a winner: Bottom-right
            for (yy in 0 until sizeY) {
                yield(GridLine(loopAdd(this@Map2DX, 0, yy, 1, 1)))
            }
            for (xx in 1 until sizeX) {
                yield(GridLine(loopAdd(this@Map2DX, xx, 0, 1, 1)))
            }

            // Scan diagonals for a winner: Bottom-left
            for (xx in 0 until sizeX) {
                yield(GridLine(loopAdd(this@Map2DX, xx, 0, -1, 1)))
            }
            for (yy in 1 until sizeY) {
                yield(GridLine(loopAdd(this@Map2DX, this@Map2DX.sizeX - 1, yy, -1, 1)))
            }
        }
    }

    fun view(viewFunction: (T) -> Any): Any {
        return this.grid.map { row -> row.map(viewFunction) }
    }

}
