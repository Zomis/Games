package net.zomis.games.impl.planetx

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.yield
import net.zomis.games.cards.CardZone
import net.zomis.games.common.fmod
import kotlin.math.abs
import kotlin.math.exp

object PlanetX {
    val LIST_OF_ZERO = listOf(0)

    enum class StarObject(private val count: Int, private val countExpert: Int? = null) {
        Comet(2),
        Asteroid(4),
        DwarfPlanet(1, 4),
        GasCloud(2),
        PlanetX(1),
        TrulyEmpty(2, 5),
        ;

        fun count(expert: Boolean): Int = countExpert?.takeIf { expert } ?: count
        fun list(expert: Boolean): List<StarObject> = (1..count(expert)).map { this }
    }

    val possibleTheories = setOf(
        StarObject.Comet, StarObject.Asteroid, StarObject.DwarfPlanet, StarObject.GasCloud,
    )

    class SectorRange(start: Int, stop: Int, expert: Boolean) : Iterable<Int> {
        private val size = if (expert) 18 else 12
        private val iterable = if (start < stop) {
            start..stop
        } else {
            (start until size).toList() + (1..stop).toList()
        }
        override fun iterator(): Iterator<Int> = iterable.iterator()
    }

    class Search(val expert: Boolean) {

        fun sectorRange(start: Int, stop: Int) = SectorRange(start, stop, expert)

        /*
        * Asteroid adjacent to at least one other asteroid (either pairs, or all 4 together)
        * Dwarf planets in a band of 6, not adjacent to PlanetX
        * Gas Clouds adjacent to at least one truly empty
        *
        * 2 Comets 7 nCr 2
        * 4 Dwarf Planets: 6 combinations * 16 starting sectors
        *
        * 4 Asteroids: 12 options for starting sector for first pair * 10 options for second pair.
        * 2 Gas Cloud: 8 remaining sectors nCr 2
        * 1 Planet X: 6 remaining sectors
        * Total: (7 nCr 2) * (6 * 16) * 12 * 10 * (8 nCr 2) * 6 = 40 642 560
        *
        * OR
        * 1 Planet X: 18 sectors
        * 4 Dwarf Planets: 6 combinations * 10 sectors.
        */

        suspend fun firstGame() {
            val game = StarGame(expert = true)
            val all = Generate(game).iterateUse().toList()
            all.printProbabilities("ALL")

            // Start info
            val afterStart = all.filter {
                it.sector(2) != StarObject.GasCloud && it.sector(3) != StarObject.DwarfPlanet &&
                    it.sector(7) != StarObject.GasCloud && it.sector(13) != StarObject.Asteroid
            }

            afterStart.printProbabilities("After start")

            var progress = afterStart
            progress = progress.filter { it.survey(2..7, StarObject.Comet) == 1 }
            progress.printProbabilities("Action")

            // Bot theory 9 10
            progress = progress.filter { it.sector(9) in possibleTheories && it.sector(10) in possibleTheories }

            // Theories after 3, 6, 9, 12, 15, 18. X Conference after 7 and 16
            // Simple mode: 3, 6, 9, 12. X Conference after 10.

            progress = progress.filter {
                // Research: At least one Comet is adjacent to 1 Dwarf Planet
                it.allIndicesOf(StarObject.Comet).any { i ->
                    it.allIndicesOf(StarObject.DwarfPlanet).any { j -> game.sectorDistance(i, j) == 1 }
                }
            }
            progress.printProbabilities("Action")

            progress = progress.filter { it.survey(5..12, StarObject.Asteroid) == 2 }

            // Bot theory 11
            progress = progress.filter { it.sector(11) in possibleTheories }
            progress.printProbabilities("Action")

            progress = progress.filter {
                // Research: All comets within 2 sectors of an asteroid
                it.allIndicesOf(StarObject.Comet).all { i ->
                    it.allIndicesOf(StarObject.Asteroid).any { j -> game.sectorDistance(i, j) <= 2 }
                }
            }
            // Conference: Planet X is within two sectors of an asteroid
            progress = progress.filter {
                val planetX = it.indexOfPlanetX()
                it.allIndicesOf(StarObject.Asteroid).any { a -> game.sectorDistance(a, planetX) <= 2 }
            }
            progress.printProbabilities("Action")

            progress = progress.filter { it.survey(8..14, StarObject.DwarfPlanet) == 4 }

            // Bot theory 13, 12
            progress = progress.filter { it.sector(13) in possibleTheories && it.sector(12) in possibleTheories }

            // Reveal bot theories for 9 and 10
            progress = progress.filter { it.sector(9) == StarObject.Asteroid && it.sector(10) == StarObject.Asteroid }
            progress.printProbabilities("Action")

            progress = progress.filter { it.survey(12..18, StarObject.TrulyEmpty) == 2 }
            progress = progress.filter {
                // Research: No Dwarf Planet within 3 sectors of a Gas Cloud
                it.allIndicesOf(StarObject.DwarfPlanet).none { i ->
                    it.allIndicesOf(StarObject.GasCloud).any { j -> game.sectorDistance(i, j) <= 3 }
                }
            }
            progress.printProbabilities("Action")

            // Bot theory 8 15
            progress = progress.filter { it.sector(8) in possibleTheories && it.sector(15) in possibleTheories }
            // Reveal bot theory for 11
            progress = progress.filter { it.sector(11) == StarObject.DwarfPlanet }

            // Survey asteroid 13-1, result 2
            progress = progress.filter { it.survey(sectorRange(13, 1), StarObject.Asteroid) == 2 }
            progress.printProbabilities("Action")

            // Survey asteroid 17-5, result 0
            progress = progress.filter { it.survey(sectorRange(17, 5), StarObject.Asteroid) == 0 }

            // Bot theory 16 7
            // Reveal bot theories for 12 and 13
            progress = progress.filter { it.sector(16) in possibleTheories && it.sector(7) in possibleTheories }
            progress = progress.filter { it.sector(12) == StarObject.DwarfPlanet && it.sector(13) == StarObject.DwarfPlanet }
            progress.printProbabilities("Action")

            // Survey gas cloud 17-2, result 0
            progress = progress.filter { it.survey(sectorRange(17, 2), StarObject.GasCloud) == 0 }

            // Bot theory 17
            // Reveal bot theories for 8 and 15
            progress = progress.filter { it.sector(17) in possibleTheories }
            progress = progress.filter { it.sector(8) == StarObject.DwarfPlanet && it.sector(15) == StarObject.Asteroid }

            progress.printProbabilities("Action")

            // TODO: Filter on what I researched and all my actions. Maybe also consider opponent actions. (That is how I found planet X after all)

        }

        suspend fun f() {
            firstGame()

//
//            val cards = CardsAnalyze2<StarObject>()
//
//            val expert = true
//            cards.addCards(allObjects(expert))
//            val zones = sectors(expert)
//            zones.forEach { cards.addZone(it) }
//            val objects = allObjects(expert)
//            zones.forEachIndexed { index, cardZone -> cardZone.add(objects[index]) }

//            cards.solve()
//            cards.addRule() // Rules are crossing multiple zones, might also need dynamic rules

        }

    }
}

private fun List<StarMap>.printProbabilities(title: String) {
    println("$title: ${this.size}")
    this.probabilities().forEach { println(it) }
    this.probabilitiesBySector().forEachIndexed { index, map ->
        println("Sector ${index + 1}: $map")
    }
    println()
}

private fun List<StarMap>.probabilities(): Map<PlanetX.StarObject, List<Double>> {
    if (this.isEmpty()) return emptyMap()
    val sectorCount = this[0].size
    val sectorRange = 0 until sectorCount
    val countMap = PlanetX.StarObject.values().associateWith { sectorRange.map { 0 }.toMutableList() }.toMutableMap()

    for (solution in this) {
        for (i in solution.indices) {
            val obj = solution[i]
            countMap.getValue(obj)[i]++
        }
    }
    return countMap.mapValues { it.value.map { sector -> sector.toDouble() / this.size } }
}
private fun List<StarMap>.probabilitiesBySector(): List<Map<PlanetX.StarObject, Double>> {
    if (this.isEmpty()) return emptyList()
    val sectorCount = this[0].size
    val sectorRange = 0 until sectorCount
    val countMap = sectorRange.map { mutableMapOf<PlanetX.StarObject, Int>() }.toMutableList()

    for (solution in this) {
        for (sectorIndex in solution.indices) {
            val obj = solution[sectorIndex]
            val probabilityMap = countMap[sectorIndex]
            probabilityMap[obj] = probabilityMap.getOrElse(obj) { 0 } + 1
        }
    }
    return countMap.map { it.mapValues { e -> e.value.toDouble() / this.size } }
}
