package net.zomis.games.components.grids

import net.zomis.games.components.Point
import net.zomis.games.components.Rect
import net.zomis.games.dsl.GameSerializable
import kotlin.math.absoluteValue
import kotlin.math.sqrt

enum class HexGridAdjust {
    OddR,
    EvenR,
    OddQ,
    EvenQ,
    ;

    fun axialToOffset(hex: HexPoint): Point = when (this) {
        OddQ -> {
            val col = hex.q
            val row = hex.r + (hex.q - (hex.q.and(1))) / 2
            Point(col, row)
        }
        OddR -> TODO()
        EvenR -> TODO()
        EvenQ -> TODO()
    }

    fun offsetToHex(hex: Point): HexPoint = when (this) {
        OddQ -> {
            val q = hex.x
            val r = hex.y - (hex.x - (hex.x.and(1))) / 2
            HexPoint(q, r)
        }
        OddR -> TODO()
        EvenR -> TODO()
        EvenQ -> TODO()
    }

}

data class Point3d(val x: Int, val y: Int, val z: Int) : GameSerializable {
    fun abs(): Point3d = Point3d(this.x.absoluteValue, this.y.absoluteValue, this.z.absoluteValue)
    fun manhattanDistance(other: Point3d): Int = kotlin.math.abs(x - other.x) + kotlin.math.abs(y - other.y) + kotlin.math.abs(z - other.z)
    fun manhattanDistance(): Int = x.absoluteValue + y.absoluteValue + z.absoluteValue
    operator fun plus(other: Point3d): Point3d = Point3d(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Point3d): Point3d = Point3d(x - other.x, y - other.y, z - other.z)
    fun toStateString(): String = "${x},${y},${z}"
    override fun serialize(): Any = toStateString()
    operator fun times(multiplier: Int): Point3d = Point3d(x * multiplier, y * multiplier, z * multiplier)

    companion object {
        fun fromString(string: String): Point3d {
            val parts = string.split(',')
            return Point3d(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
        }
    }
}

data class HexPoint(val q: Int, val r: Int) {
    val s get() = -q - r
    fun toCube(): Point3d = Point3d(q, r, s)
    fun distance(other: HexPoint): Int {
        val diff = toCube() - other.toCube()
        return maxOf(diff.x.absoluteValue, diff.y.absoluteValue, diff.z.absoluteValue)
    }

    fun toStateString(): String = "$q,$r"
}
data class HexPointField<T>(val hex: HexPoint, val value: T)

class HexGrid<T>(
    val sizeX: Int,
    val sizeY: Int,
    val gridAdjust: HexGridAdjust,
    factory: (x: Int, y: Int) -> T,
) {
    private val grid: Grid<T> = GridImpl(sizeX, sizeY, factory)

    fun set(hex: HexPoint, value: T) {
        val offset = gridAdjust.axialToOffset(hex)
        grid.set(offset.x, offset.y, value)
    }
    fun get(point: HexPoint): T {
        val offset = gridAdjust.axialToOffset(point)
        return grid.get(offset.x, offset.y)
    }
    fun getOrNull(hex: HexPoint): T? {
        val offset = gridAdjust.axialToOffset(hex)
        return grid.getOrNull(offset.x, offset.y)
    }

    fun points(): Iterable<HexPoint> = grid.points().map { gridAdjust.offsetToHex(it) }
    fun values(): Iterable<HexPointField<T>> = points().map {
        HexPointField(it, get(it))
    }

    fun <R> hexView(function: (T) -> R): List<HexPointField<R>> = points().map {
        HexPointField(it, function.invoke(get(it)))
    }

}