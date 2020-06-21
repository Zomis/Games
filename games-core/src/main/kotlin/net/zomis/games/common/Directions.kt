package net.zomis.games.common

enum class Direction4(val deltaX: Int, val deltaY: Int) {
    LEFT(-1, 0),
    RIGHT(1, 0),
    UP(0, -1),
    DOWN(0, 1),
    ;
}
