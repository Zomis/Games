package net.zomis.games.common

import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.sqrt

data class Point(val x: Int, val y: Int) {
    fun abs(): Point = Point(this.x.absoluteValue, this.y.absoluteValue)
    fun distance(): Double = sqrt(this.x.toDouble() * this.x + this.y.toDouble() * this.y)
    fun manhattanDistance(other: Point): Int = abs(x - other.x) + abs(y - other.y)
    operator fun plus(other: Point) = Point(x + other.x, y + other.y)
    operator fun minus(other: Point): Point = Point(x - other.x, y - other.y)
}

data class PointMove(val source: Point, val destination: Point)

data class Grid2D(val width: Int, val height: Int) {
    fun points(): Sequence<Point> = (0 until height).asSequence().flatMap { y -> (0 until width).asSequence().map { x -> Point(x, y) } }
}
