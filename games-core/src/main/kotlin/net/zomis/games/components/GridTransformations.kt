package net.zomis.games.components

import net.zomis.Best
import net.zomis.games.Position
import net.zomis.games.Transformation

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
