package net.zomis.games.common

import kotlin.math.*

data class Point(val x: Int, val y: Int) {
    fun abs(): Point = Point(this.x.absoluteValue, this.y.absoluteValue)
    fun distance(): Double = sqrt(this.x.toDouble() * this.x + this.y.toDouble() * this.y)
    fun manhattanDistance(other: Point): Int = abs(x - other.x) + abs(y - other.y)
    operator fun plus(other: Point) = Point(x + other.x, y + other.y)
    operator fun minus(other: Point): Point = Point(x - other.x, y - other.y)
    fun toStateString(): String = "${x},${y}"
}

data class Rect(val top: Int, val left: Int, val right: Int, val bottom: Int) {
    fun normalized(): Rect {
        val xs = listOf(left, right).sorted()
        val ys = listOf(top, bottom).sorted()
        return Rect(top = ys[0], left = xs[0], right = xs[1], bottom = ys[1])
    }

    fun contains(x: Int, y: Int): Boolean = x in left..right && y in top..bottom

    fun expand(extraRadius: Int): Rect = Rect(
        top = top - extraRadius,     left = left - extraRadius,
        right = right + extraRadius, bottom = bottom + extraRadius
    )

    fun include(x: Int, y: Int): Rect
        = Rect(left = min(left, x), right = max(right, x), top = min(top, y), bottom = max(bottom, y))

    fun intersects(other: Rect): Boolean {
        if (this.bottom < other.top) return false
        if (this.right < other.left) return false
        if (this.left > other.right) return false
        if (this.top > other.bottom) return false
        return true
    }

    fun points(): Sequence<Point> = (top..bottom).asSequence().flatMap { y ->
        (left..right).asSequence().map { x -> Point(x, y) }
    }

    val width: Int get() = right - left + 1
    val height: Int get() = bottom - top + 1
}

data class PointMove(val source: Point, val destination: Point)

data class Grid2D(val width: Int, val height: Int) {
    fun points(): Sequence<Point> = (0 until height).asSequence().flatMap { y -> (0 until width).asSequence().map { x -> Point(x, y) } }
}
