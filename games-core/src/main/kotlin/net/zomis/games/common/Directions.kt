package net.zomis.games.common

enum class Direction4(val deltaX: Int, val deltaY: Int) {
    LEFT(-1, 0),
    RIGHT(1, 0),
    UP(0, -1),
    DOWN(0, 1),
    ;

    fun order(): Int {
        return when (this) {
            UP -> 0
            LEFT -> 1
            RIGHT -> 2
            DOWN -> 3
        }
    }

    fun delta(): Point = Point(deltaX, deltaY)
}

enum class Direction8 constructor(val deltaX: Int, val deltaY: Int) {
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
}
