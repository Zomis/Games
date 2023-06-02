package net.zomis.minesweeper.analyze.factory

import kotlin.jvm.JvmOverloads

class General2DAnalyze @JvmOverloads constructor(
    map: Array<String>,
    var hiddenMines: Int = 0,
    neighbors: Array<IntArray> = DEFAULT_NEIGHBORS
) : AbstractAnalyze<CharPoint>() {
    private val neighbors: Array<IntArray>
    private val points: Array<Array<CharPoint>>
    private val width: Int
    private val height: Int
    public override val remainingMinesCount: Int

    init {
        for (neighbor in neighbors) {
            require(neighbor.size == 2) {
                "Neighbor array must be an array of int[2] (x, y) pair. Unexpected length " + neighbor.size +
                        " for " + neighbor.contentToString()
            }
        }
        width = map[0].length
        height = map.size
        points = (0 until width).map { x ->
            (0 until height).map { y ->
                val ch = map[y][x]
                require(isValidChar(ch)) { "'$ch' is not a valid character" }
                if (ch == HIDDEN_MINE) {
                    hiddenMines++
                }
                CharPoint(x, y, ch)
            }.toTypedArray()
        }.toTypedArray()
        remainingMinesCount = hiddenMines
        this.neighbors = neighbors
        this.createRules(allPoints)
    }

    private fun isValidChar(ch: Char): Boolean {
        return ch.isDigit() || ch == BLOCKED || ch == HIDDEN_MINE || ch == KNOWN_MINE || isInArray(
            ch,
            UNCLICKED
        )
    }

    public override val allPoints: List<CharPoint>
        get() {
            val point: MutableList<CharPoint> = mutableListOf()
            for (ps in points) {
                for (p in ps) {
                    point.add(p)
                }
            }
            return point
        }

    public override fun fieldHasRule(field: CharPoint): Boolean {
        return !isBlocked(field) && isClicked(field) && !isDiscoveredMine(field)
    }

    private fun isBlocked(field: CharPoint): Boolean {
        return field.value == BLOCKED
    }

    public override val allUnclickedFields: List<CharPoint> get() = points.flatten().filter { !isClicked(it) }

    public override fun isDiscoveredMine(field: CharPoint): Boolean = field.value == KNOWN_MINE

    public override fun getFieldValue(field: CharPoint): Int = field.value.digitToIntOrNull() ?: -1

    public override fun getNeighbors(field: CharPoint): List<CharPoint> {
        val neighbors: MutableList<CharPoint> = ArrayList<CharPoint>(neighbors.size)
        val x = field.x
        val y = field.y
        for (xx in x - 1..x + 1) {
            for (yy in y - 1..y + 1) {
                if (xx == x && yy == y) continue
                if (xx < 0 || yy < 0) continue
                if (xx >= width || yy >= height) continue
                neighbors.add(points[xx][yy])
            }
        }
        return neighbors
    }

    public override fun isClicked(field: CharPoint): Boolean {
        return if (isBlocked(field)) true else !isUnclickedChar(field.value) && field.value != HIDDEN_MINE
    }

    private fun isUnclickedChar(value: Char): Boolean {
        return isInArray(value, UNCLICKED)
    }

    private fun isInArray(value: Char, array: CharArray): Boolean {
        for (ch in array) {
            if (ch == value) return true
        }
        return false
    }

    fun getPoint(x: Int, y: Int): CharPoint {
        return points[x][y]
    }

    companion object {
        val UNCLICKED = charArrayOf('_', '?')
        const val HIDDEN_MINE = 'x'
        const val KNOWN_MINE = '!'
        const val BLOCKED = '#'
        private val DEFAULT_NEIGHBORS = arrayOf(
            intArrayOf(-1, -1),
            intArrayOf(0, -1),
            intArrayOf(1, -1),
            intArrayOf(-1, 0),
            intArrayOf(1, 0),
            intArrayOf(-1, 1),
            intArrayOf(0, 1),
            intArrayOf(1, 1)
        )
    }
}