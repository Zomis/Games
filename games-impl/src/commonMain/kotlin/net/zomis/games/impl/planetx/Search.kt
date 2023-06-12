package net.zomis.games.impl.planetx

class Search(val game: StarGame, private val possibilities: MutableList<StarMap>) {
    fun isEmpty(): Boolean = possibilities.isEmpty()
    val size get() = possibilities.size

    fun exclude(
        vararg sectorsCannotBe: Pair<Int, PlanetX.StarObject>
    ) {
        filter { stars ->
            sectorsCannotBe.all { (sector, obj) ->
                stars.sector(sector) != obj
            }
        }
    }

    fun filter(predicate: (StarMap) -> Boolean) {
        possibilities.retainAll(predicate)
    }

    fun sectorsCanHaveTheories(sector1: Int, sector2: Int? = null) {
        filter {
            it.sector(sector1) in PlanetX.possibleTheories
        }
        if (sector2 != null) {
            filter {
                it.sector(sector2) in PlanetX.possibleTheories
            }
        }
    }

    fun printProbabilities(title: String) {
        println("$title: ${this.size}")
        this.probabilities().forEach { println(it) }
        this.probabilitiesBySector().forEachIndexed { index, map ->
            println("Sector ${index + 1}: $map")
        }
        println()
    }

    fun probabilities(): Map<PlanetX.StarObject, List<Double>> {
        if (this.isEmpty()) return emptyMap()
        val countMap = PlanetX.StarObject.values().associateWith { game.range.map { 0 }.toMutableList() }.toMutableMap()

        for (solution in this.possibilities) {
            for (i in solution.indices) {
                val obj = solution[i]
                countMap.getValue(obj)[i]++
            }
        }
        return countMap.mapValues { it.value.map { sector -> sector.toDouble() / this.size } }
    }
    fun probabilitiesBySector(): List<Map<PlanetX.StarObject, Double>> {
        if (this.isEmpty()) return emptyList()
        val countMap = game.range.map { mutableMapOf<PlanetX.StarObject, Int>() }.toMutableList()

        for (solution in this.possibilities) {
            for (sectorIndex in solution.indices) {
                val obj = solution[sectorIndex]
                val probabilityMap = countMap[sectorIndex]
                probabilityMap[obj] = probabilityMap.getOrElse(obj) { 0 } + 1
            }
        }
        return countMap.map { it.mapValues { e -> e.value.toDouble() / this.size } }
    }

}