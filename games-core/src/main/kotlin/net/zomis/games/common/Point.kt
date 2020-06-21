package net.zomis.games.common

import kotlin.math.absoluteValue
import kotlin.math.sqrt

data class Point(val x: Int, val y: Int) {
    fun minus(other: Point): Point {
        return Point(x - other.x, y - other.y)
    }

    fun abs(): Point = Point(this.x.absoluteValue, this.y.absoluteValue)
    fun distance(): Double = sqrt(this.x.toDouble() * this.x + this.y.toDouble() * this.y)
}

data class PointMove(val source: Point, val destination: Point)
