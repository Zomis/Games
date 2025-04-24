package net.zomis.games.components

import net.zomis.games.components.grids.Transformation
import net.zomis.games.components.grids.TransformationType
import net.zomis.games.dsl.GameSerializable

enum class Direction4(val deltaX: Int, val deltaY: Int) {
    LEFT(-1, 0),
    RIGHT(1, 0),
    UP(0, -1),
    DOWN(0, 1),
    ;

    val isHorizontal get() = this == LEFT || this == RIGHT
    val isVertical get() = this == UP || this == DOWN

    fun order(): Int {
        return when (this) {
            UP -> 0
            LEFT -> 1
            RIGHT -> 2
            DOWN -> 3
        }
    }

    fun opposite(): Direction4 = this.rotateClockwise().rotateClockwise()

    fun rotateClockwise(): Direction4 = when (this) {
        UP -> RIGHT
        RIGHT -> DOWN
        DOWN -> LEFT
        LEFT -> UP
    }

    fun delta(): Point = Point(deltaX, deltaY)

    fun transform(transformation: Transformation): Direction4 {
        return transformation.transformations.fold(this) { acc, next ->
            when (next) {
                TransformationType.ROTATE -> acc.rotateClockwise()
                TransformationType.FLIP_X -> if (acc.isHorizontal) acc.opposite() else acc
                TransformationType.FLIP_Y -> if (acc.isVertical) acc.opposite() else acc
            }
        }
    }
}

enum class Direction8 constructor(val deltaX: Int, val deltaY: Int): GameSerializable {
    W(-1, 0),
    NW(-1, -1),
    N(0, -1),
    NE(1, -1),
    E(1, 0),
    SE(1, 1),
    S(0, 1),
    SW(-1, 1),
    ;

    fun delta(): Point = Point(deltaX, deltaY)

    fun rotateClockwise() = when (this) {
        W -> NW
        NW -> N
        N -> NE
        NE -> E
        E -> SE
        SE -> S
        S -> SW
        SW -> W
    }

    fun opposite(): Direction8 = when (this) {
        W -> E
        E -> W
        NW -> SE
        SE -> NW
        N -> S
        S -> N
        NE -> SW
        SW -> NE
    }

    override fun serialize(): Any = delta().serialize()

    companion object {
        fun diagonals() = listOf(NW, NE, SW, SE)
    }
}
