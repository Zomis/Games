package net.zomis.games.impl.ttt

import net.zomis.games.Map2D

typealias TTT3DAI = (TTT3D) -> Pair<Int, Int>
private val RANGE: IntRange = (0 until 4)

enum class TTT3DPiece(val playerIndex: Int) {
    X(0), O(1);

    fun opponent(): TTT3DPiece = if (this == X) O else X
}

data class TTT3DPoint(val x: Int, val y: Int, val z: Int, var piece: TTT3DPiece?)

data class TTT3DWinCondition(val pieces: List<TTT3DPoint>) {

    fun contains(point: TTT3DPoint?): Boolean {
        return pieces.contains(point)
    }

    fun canWin(player: TTT3DPiece): Boolean {
        return pieces.none { it.piece != null && it.piece != player }
    }

    fun emptySpaces(): Int {
        return pieces.count { it.piece == null }
    }

}

class TTT3D {

    val pieces: Array<Array<Array<TTT3DPoint>>> = RANGE.map { y ->
        RANGE.map { x ->
            RANGE.map { z -> TTT3DPoint(x, y, z, null) }.toTypedArray()
        }.toTypedArray()
    }.toTypedArray()
    var currentPlayer = TTT3DPiece.X

    val winConditions: List<TTT3DWinCondition>

    init {
        val conditions = mutableListOf<TTT3DWinCondition>()

        RANGE.forEach { a ->
            RANGE.forEach { b ->
                conditions.add(findConditions(a to 0, b to 0, 0 to 1))
                conditions.add(findConditions(a to 0, 0 to 1, b to 0))
                conditions.add(findConditions(0 to 1, a to 0, b to 0))
            }
            // Diagonals per plane
            conditions.add(findConditions(0 to 1, 0 to 1, a to 0))
            conditions.add(findConditions(RANGE.last to -1, 0 to 1, a to 0))

            // Diagonals per row
            conditions.add(findConditions(a to 0, 0 to 1, 0 to 1))
            conditions.add(findConditions(a to 0, RANGE.last to -1, 0 to 1))

            // Diagonals per column
            conditions.add(findConditions(0 to 1, a to 0, 0 to 1))
            conditions.add(findConditions(RANGE.last to -1, a to 0, 0 to 1))
        }

        // Diagonals from bottom corner to top opposite corner
        conditions.add(findConditions(0 to 1, 0 to 1, 0 to 1))
        conditions.add(findConditions(RANGE.last to -1, 0 to 1, 0 to 1))
        conditions.add(findConditions(RANGE.last to -1, RANGE.last to -1, 0 to 1))
        conditions.add(findConditions(0 to 1, RANGE.last to -1, 0 to 1))

        winConditions = conditions
    }

    private fun findConditions(y: Pair<Int, Int>, x: Pair<Int, Int>, z: Pair<Int, Int>): TTT3DWinCondition {
        var yy = y.first
        var xx = x.first
        var zz = z.first
        val max = RANGE.last
        val result = mutableListOf<TTT3DPoint>()
        while (xx <= max && yy <= max && zz <= max) {
            val tile = pieces[yy][xx][zz]
            result.add(tile)

            xx += x.second
            yy += y.second
            zz += z.second
        }
        if (result.size != 4) {
            throw IllegalStateException("Unexpected number of results. Expected 4. $result")
        }
        return TTT3DWinCondition(result)
    }

    fun playAt(y: Int, x: Int): Boolean {
        if (!RANGE.contains(x) || !RANGE.contains(y)) {
            return false
        }
        val spot = this.pieces[y][x]
        val point = spot.firstOrNull { it.piece == null }
        point?.piece = currentPlayer
        if (point != null) {
            currentPlayer = currentPlayer.opponent()
            return true
        } else {
            return false
        }
    }

    fun findWinner(): TTT3DPiece? {
        return winConditions.find { winCond ->
            val pieces = winCond.pieces.map { list -> list.piece }
            val notNull = pieces.filterNotNull()
            return@find notNull.size == 4 && pieces.all { it == pieces.first() }
        }?.pieces?.firstOrNull()?.piece
    }

    fun canPlayAt(y: Int, x: Int): Boolean {
        return this.pieces[y][x].any { it.piece == null }
    }

    fun get(y: Int, x: Int, z: Int): TTT3DPiece? = this.pieces[y][x][z].piece

    fun canPlayAt(field: TTT3DPoint): Boolean {
        return field.piece == null &&
            this.pieces[field.y][field.x].firstOrNull { it.piece == null } == field
    }

    fun isDraw(): Boolean {
        return findWinner() == null && allFields().none { it.piece == null }
    }

    fun allFields(): Sequence<TTT3DPoint> {
        return this.pieces.flatten().flatMap { it.toList() }.asSequence()
    }

    fun copy(): TTT3D {
        val other = TTT3D()
        this.allFields().forEach { other.pieces[it.y][it.x][it.z].piece = it.piece }
        other.currentPlayer = this.currentPlayer
        return other
    }

    override fun toString(): String {
        return "Player $currentPlayer Fields ${allFields().toList()}"
    }

    fun isGameOver(): Boolean {
        return findWinner() != null || isDraw()
    }

    fun standardize() {
        val getter: (x: Int, y: Int) -> List<TTT3DPiece?> = { x, y ->
            this.pieces[y][x].map { it.piece }
        }
        val setter: (x: Int, y: Int, v: List<TTT3DPiece?>) -> Unit = { x, y, v ->
            this.pieces[y][x].forEachIndexed {i, point ->
                point.piece = v[i]
            }
        }

        Map2D(4, 4, getter, setter).standardize {
            it.mapIndexed{a, b -> a to b}.fold(0) { curr, next -> curr * 10 + (next.second?.ordinal ?: 5) }
        }
    }

}
