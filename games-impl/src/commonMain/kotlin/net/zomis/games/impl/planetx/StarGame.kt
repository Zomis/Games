package net.zomis.games.impl.planetx

import kotlinx.coroutines.flow.toList
import net.zomis.games.common.fmod
import kotlin.math.abs
import kotlin.math.sign

class SectorRange(start: Int, stop: Int, expert: Boolean) : Iterable<Int> {
    private val size = if (expert) 18 else 12
    private val iterable = if (start <= stop) {
        start..stop
    } else {
        (start until size).toList() + (1..stop).toList()
    }
    override fun iterator(): Iterator<Int> = iterable.iterator()
}

class StarGame(val expert: Boolean) {
    val sectorCount: Int = if (expert) 18 else 12
    val halfSize = sectorCount / 2
    val validCometSectors = setOf(2, 3, 5, 7, 11, 13, 17).toSet()
    @Deprecated("not clear that it's using index and not sector")
    val validCometLocations = validCometSectors.map { it - 1 }.toSet()
    val range = 0 until sectorCount

    fun allObjects(): List<PlanetX.StarObject> = PlanetX.StarObject.values().flatMap { it.list(expert) }
    fun random(): StarMap = StarMap(game = this, list = allObjects().shuffled().toMutableList())

    fun sectorRange(start: Int, stop: Int) = SectorRange(start, stop, expert)
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

    suspend fun startSearch(): Search {
        val possibilities = Generate(this).iterateUse().toList().toMutableList()
        return Search(this, possibilities)
    }

    fun countOf(starObject: PlanetX.StarObject): Int = starObject.count(this.expert)
    fun visibleSkyFrom(startSector: Int): SectorRange = this.sectorRange(startSector, startSector + halfSize - 1)
    fun canHaveComet(sector: Int): Boolean = (sector fmod sectorCount) in validCometSectors

}