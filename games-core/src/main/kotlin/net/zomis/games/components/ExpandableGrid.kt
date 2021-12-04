package net.zomis.games.components

import net.zomis.games.common.Point
import net.zomis.games.common.Rect

class ExpandableGrid<T>(val chunkSize: Int = 16): Grid<T> {
    // start centered around middle of first chunk (0, 0 --> 8, 8)

    private val offset = chunkSize / 2

    class Chunk<T>(private val chunkSize: Int, val x: Int, val y: Int) {
        internal val fields: MutableList<MutableList<T?>> = (0 until chunkSize).map {
            (0 until chunkSize).map {
                null as T?
            }.toMutableList()
        }.toMutableList()

        fun set(x: Int, y: Int, value: T?) {
            fields[y.mod(chunkSize)][x.mod(chunkSize)] = value
        }

        fun get(x: Int, y: Int): T? = fields[y.mod(chunkSize)][x.mod(chunkSize)]
    }

    private val chunks = mutableMapOf<Point, Chunk<T>>()
    val chunkCount get() = chunks.size

    private fun chunk(x: Int, y: Int): Chunk<T> {
        val chunkX = (x + offset).floorDiv(chunkSize)
        val chunkY = (y + offset).floorDiv(chunkSize)
        val point = Point(chunkX, chunkY)
        return chunks.getOrPut(point) { Chunk(chunkSize, chunkX, chunkY) }
    }

    fun cropped(extraRadius: Int = 0): Grid<T> {
        return object : Grid<T> {
            override fun border(): Rect = this@ExpandableGrid.border().expand(extraRadius)
            override val sizeX: Int get() = border().width
            override val sizeY: Int get() = border().height
            override fun set(x: Int, y: Int, value: T) = this@ExpandableGrid.set(x, y, value)
            override fun get(x: Int, y: Int): T = this@ExpandableGrid.get(x, y)
            override fun getOrNull(x: Int, y: Int): T? = this@ExpandableGrid.getOrNull(x, y)
        }
    }

    private fun localToGlobal(chunk: Int, positionInChunk: Int): Int {
        return chunk * chunkSize + positionInChunk - offset
    }
    override fun border(): Rect {
        var result: Rect? = null
        for (chunk in chunks.values) {
            for (yy in chunk.fields.withIndex()) {
                for (pos in yy.value.withIndex()) {
                    if (pos.value != null) {
                        val globalX = localToGlobal(chunk.x, pos.index)
                        val globalY = localToGlobal(chunk.y, yy.index)
                        result = result?.include(globalX, globalY)
                            ?: Rect(left = globalX, top = globalY, right = globalX, bottom = globalY)
                    }
                }
            }
        }
        return result ?: throw IllegalStateException("ExpandableGrid contains nothing")
    }

    override val sizeX: Int get() = border().width
    override val sizeY: Int get() = border().height

    override fun set(x: Int, y: Int, value: T) = chunk(x, y).set(x + offset, y + offset, value)

    override fun get(x: Int, y: Int): T = chunk(x, y).get(x + offset, y + offset)!!

    override fun getOrNull(x: Int, y: Int): T? = chunk(x, y).get(x + offset, y + offset)

}
