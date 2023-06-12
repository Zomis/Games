package net.zomis.games.impl.planetx

import kotlin.math.abs

class StarGame(val expert: Boolean) {
    val sectorCount: Int = if (expert) 18 else 12
    val halfSize = sectorCount / 2
    val validCometLocations = setOf(2, 3, 5, 7, 11, 13, 17).map { it - 1 }.toSet()
    val range = 0 until sectorCount

    fun allObjects(): List<PlanetX.StarObject> = PlanetX.StarObject.values().flatMap { it.list(expert) }
    fun random(): StarMap = StarMap(game = this, list = allObjects().shuffled().toMutableList())

    fun sectorDistance(a: Int, b: Int): Int {
        val distance = abs(a - b)
        return if (distance > halfSize) sectorCount - distance else distance
    }

    fun biggestDistance(sectors: List<Int>): Int {
        var max = 0
        for (i in sectors) {
            for (j in sectors) {
                val distance = sectorDistance(i, j)
                if (distance > max) max = distance
            }
        }
        return max
    }

    fun asteroidIndicesMatch(asteroidIndices: List<Int>): Boolean {
        val (a, b, c, d) = asteroidIndices

        return if (sectorDistance(a, b) < sectorDistance(a, d)) {
            sectorDistance(a, b) == 1 && sectorDistance(c, d) == 1
        } else {
            sectorDistance(d, a) == 1 && sectorDistance(b, c) == 1
        }
    }


}