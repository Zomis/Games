package net.zomis.games.components

import net.zomis.games.dsl.GameSerializable
import kotlin.math.*

data class Point(val x: Int, val y: Int): GameSerializable {
    fun abs(): Point = Point(this.x.absoluteValue, this.y.absoluteValue)
    fun distance(): Double = sqrt(this.x.toDouble() * this.x + this.y.toDouble() * this.y)
    fun manhattanDistance(other: Point): Int = abs(x - other.x) + abs(y - other.y)
    fun manhattanDistance(): Int = abs(x) + abs(y)
    operator fun plus(other: Point) = Point(x + other.x, y + other.y)
    operator fun minus(other: Point): Point = Point(x - other.x, y - other.y)
    fun toStateString(): String = "${x},${y}"
    override fun serialize(): Any = toStateString()
    operator fun times(multiplier: Int): Point = Point(x * multiplier, y * multiplier)
    fun topLeftOfRect(sizeX: Int, sizeY: Int): Rect = Rect(y, x, x + sizeX - 1, y + sizeY - 1)

    companion object {
        fun fromString(string: String): Point {
            val x = string.substringBefore(',').toInt()
            val y = string.substringAfter(',').toInt()
            return Point(x, y)
        }
    }
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

    fun width(): Int = right - left + 1
    fun height(): Int = bottom - top + 1
    fun area(): Int = width() * height()
    fun isWithin(other: Rect): Boolean = other.covers(this)
    fun covers(other: Rect): Boolean = this.left <= other.left && this.top <= other.top && this.right >= other.right && this.bottom >= other.bottom
}

data class PointMove(val source: Point, val destination: Point)

data class Grid2D(val width: Int, val height: Int) {
    fun points(): Sequence<Point> = (0 until height).asSequence().flatMap { y -> (0 until width).asSequence().map { x -> Point(x, y) } }
}
