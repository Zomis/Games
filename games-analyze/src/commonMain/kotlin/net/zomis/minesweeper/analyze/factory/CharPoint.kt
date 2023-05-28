package net.zomis.minesweeper.analyze.factory

class CharPoint(val x: Int, val y: Int, val value: Char) {

    override fun hashCode(): Int {
        val prime = 31
        var result = 1
        result = prime * result + x
        result = prime * result + y
        return result
    }

    override fun equals(obj: Any?): Boolean {
        if (this === obj) return true
        if (obj !is CharPoint) return false
        val other = obj
        return x == other.x && y == other.y && other.value == value
    }

    override fun toString(): String {
        return "($x, $y '$value')"
    }
}