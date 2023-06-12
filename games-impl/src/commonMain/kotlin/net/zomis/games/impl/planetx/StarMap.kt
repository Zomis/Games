package net.zomis.games.impl.planetx

import net.zomis.games.common.fmod

class StarMap(private val game: StarGame, private val list: MutableList<PlanetX.StarObject>) {
    init {
        require(game.sectorCount == list.size)
    }
    val indices = list.indices
    val size = list.size
    operator fun get(index: Int) = list[index]
    operator fun set(index: Int, value: PlanetX.StarObject) {
        list[index] = value
    }

    fun allIndicesOf(obj: PlanetX.StarObject): List<Int> {
        return list.mapIndexedNotNull { index, e ->
            if (e == obj) index else null
        }
    }

    fun wrapGet(index: Int): PlanetX.StarObject = list[index % size]
    fun available(index: Int, obj: PlanetX.StarObject): Boolean = wrapGet(index).let { it == PlanetX.StarObject.TrulyEmpty || it == obj }
    fun clearOf(obj: PlanetX.StarObject) {
        for (i in indices) {
            if (list[i] == obj) list[i] = PlanetX.StarObject.TrulyEmpty
        }
    }

    fun getWithin(index: Int, range: Int): List<PlanetX.StarObject> {
        val biggerList = list + list + list
        return biggerList.subList(index + list.size - range, index + list.size + range + 1)
    }

    fun setObjects(obj: PlanetX.StarObject, index: Int, addIndex: List<Int>): Boolean {
        for (i in indices) {
            if (list[i] == obj) list[i] = PlanetX.StarObject.TrulyEmpty
        }
        if (addIndex.any { list[(index + it) % size] != PlanetX.StarObject.TrulyEmpty }) {
            println("Could not setObjects: $obj $index $addIndex. $this")
            return false
        }

        for (i in addIndex) {
            list[(index + i) % size] = obj
        }
        return true
    }

    fun valid(): Boolean {
        // PlanetX not adjacent to a Dwarf Planet
        val planetX = list.indexOf(PlanetX.StarObject.PlanetX)
        if (getWithin(planetX, range = 1).contains(PlanetX.StarObject.DwarfPlanet)) return false

        // Comets only in sectors 2 3 5 7 11 13 17
        val comet1 = list.indexOfFirst { it == PlanetX.StarObject.Comet }
        val comet2 = list.indexOfLast { it == PlanetX.StarObject.Comet }
        if (comet1 !in game.validCometLocations || comet2 !in game.validCometLocations) return false

        // Asteroid must be adjacent to at least one other asteroid (either pairs, or all 4 together)
        val asteroidIndices = list.mapIndexedNotNull { index, starObject ->
            if (starObject == PlanetX.StarObject.Asteroid) index else null
        }
        if (!game.asteroidIndicesMatch(asteroidIndices)) return false

        // Gas Clouds adjacent to at least one truly empty
        val gas1 = list.indexOfFirst { it == PlanetX.StarObject.GasCloud }
        val gas2 = list.indexOfLast { it == PlanetX.StarObject.GasCloud }
        if (!getWithin(gas1, range = 1).contains(PlanetX.StarObject.TrulyEmpty)) return false
        if (!getWithin(gas2, range = 1).contains(PlanetX.StarObject.TrulyEmpty)) return false

        if (!game.expert) return true // Only one Dwarf Planet, condition already checked.

        // Dwarf Planets in a band of 6
        val dwarfPlanets = list.mapIndexedNotNull { index, starObject ->
            if (starObject == PlanetX.StarObject.DwarfPlanet) index else null
        }
        return game.biggestDistance(dwarfPlanets) == 5
    }

    fun emptyIndices() = allIndicesOf(PlanetX.StarObject.TrulyEmpty)
    fun copy(): StarMap = StarMap(game, list.toMutableList())
    fun sector(sector: Int): PlanetX.StarObject = this[(sector - 1) fmod size]

    fun survey(sectors: Iterable<Int>, surveyFor: PlanetX.StarObject): Int {
        var count = 0

        return if (surveyFor == PlanetX.StarObject.TrulyEmpty) {
            for (i in sectors) {
                val obj = this.sector(i)
                if (obj == PlanetX.StarObject.TrulyEmpty || obj == PlanetX.StarObject.PlanetX) count++
            }
            count
        } else {
            for (i in sectors) {
                if (this.sector(i) == surveyFor) count++
            }
            count
        }
    }

    fun indexOfPlanetX(): Int = this.list.indexOf(PlanetX.StarObject.PlanetX)

}