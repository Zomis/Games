package net.zomis.games.components

import net.zomis.Best

data class Position(val x: Int, val y: Int, val sizeX: Int, val sizeY: Int) {
    fun next(): Position? {
        if (x == sizeX - 1) {
            return if (y == sizeY - 1) null else Position(0, y + 1, sizeX, sizeY)
        }
        return Position(this.x + 1, this.y, this.sizeX, this.sizeY)
    }

    internal fun rotate(): Position
            = Position(this.sizeY - 1 - this.y, this.x, this.sizeY, this.sizeX)
    internal fun flipX(): Position
            = Position(this.sizeX - 1 - this.x, this.y, this.sizeX, this.sizeY)
    internal fun flipY(): Position
            = Position(this.x, this.sizeY - 1 - this.y, this.sizeX, this.sizeY)
    fun transform(transformation: Transformation): Position = transformation.transform(this)
}

enum class TransformationType(val transforming: (Position) -> Position, val reverse: (Position) -> Position) {
    FLIP_X(Position::flipX, Position::flipX),
    FLIP_Y(Position::flipY, Position::flipY),
    ROTATE(Position::rotate, { start -> start.rotate().rotate().rotate() }),
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

private fun <T> Grid<T>.originalPossibleTransformations(): MutableSet<Transformation> {
    val possibleTransformations = Transformation.values().toMutableSet()
    if (sizeX != sizeY) {
        // Rotating 90 or 270 degrees only works if both width or height is the same
        possibleTransformations.remove(Transformation.ROTATE_90)
        possibleTransformations.remove(Transformation.ROTATE_270)
    }
    return possibleTransformations
}

fun <T> Grid<T>.standardizedTransformation(valueFunction: (T) -> Int): Transformation {
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
            val originalT = get(originalPos.x, originalPos.y)
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

fun <T> Grid<T>.symmetryTransformations(equalsFunction: (T, T) -> Boolean): Set<Transformation> {
    val possibleTransformations = originalPossibleTransformations()
    return possibleTransformations.filter { transformation ->
        positions().all { pos ->
            val other = transformation.transform(pos)
            equalsFunction(get(pos.x, pos.y), get(other.x, other.y))
        }
    }.toSet()
}

fun <T> Grid<T>.transform(transformation: Transformation) {
    val rotated = GridImpl(sizeX, sizeY) { x, y ->
        val pos = Position(x, y, sizeX, sizeY)
        val oldPos = transformation.reverseTransform(pos)
        get(oldPos.x, oldPos.y)
    }

    (0 until sizeY).forEach {y ->
        (0 until sizeX).forEach {x ->
            set(x, y, rotated.grid[y][x])
        }
    }
}

fun <T> Grid<T>.positions(): Sequence<Position> {
    return (0 until sizeY).asSequence().flatMap { y ->
        (0 until sizeX).asSequence().map { x ->
            Position(x, y, sizeX, sizeY)
        }
    }
}

fun <T> Grid<T>.standardize(valueFunction: (T) -> Int) {
    this.transform(this.standardizedTransformation(valueFunction))
}
