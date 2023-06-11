package net.zomis.games.impl.planetx

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import net.zomis.games.cards.CardZone
import net.zomis.games.cards.probabilities.CardsAnalyze2
import net.zomis.games.impl.planetx.PlanetX.setObjects
import net.zomis.games.impl.planetx.PlanetX.wrapGet
import kotlin.math.abs
import kotlin.random.Random

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

    val validCometLocations = setOf(2, 3, 5, 7, 11, 13, 17).map { it - 1 }.toSet()
    fun allObjects(expert: Boolean): List<StarObject> = StarObject.values().flatMap { it.list(expert) }
    fun sectorCount(expert: Boolean): Int = if (expert) 18 else 12
    fun sectors(expert: Boolean): List<CardZone<StarObject>> = (1..sectorCount(expert)).map { CardZone() }

    fun random(expert: Boolean): List<StarObject> = allObjects(expert).shuffled()
    private fun sectorDistanceNormal(a: Int, b: Int): Int {
        val distance = abs(a - b)
        return if (distance > 6) 12 - distance else distance
    }
    private fun sectorDistanceExpert(a: Int, b: Int): Int {
        val distance = abs(a - b)
        return if (distance > 9) 18 - distance else distance
        /*
        * TODO: See https://codereview.stackexchange.com/a/40882/31562
        * distance      actual
        * 17            1
        * 16            2
        * 15            3
        * 14            4
        * 13            5
        * 12            6
        * 11            7
        * 10            8
        */
    }

    private fun asteroidIndicesMatch(asteroidIndices: List<Int>, expert: Boolean): Boolean {
        val (a, b, c, d) = asteroidIndices
        val sectorDistanceFunc = if (expert) ::sectorDistanceExpert else ::sectorDistanceNormal

        return if (sectorDistanceFunc(a, b) < sectorDistanceFunc(a, d)) {
            sectorDistanceFunc(a, b) == 1 && sectorDistanceFunc(c, d) == 1
        } else {
            sectorDistanceFunc(d, a) == 1 && sectorDistanceFunc(b, c) == 1
        }
    }

    private fun getWithin(list: List<StarObject>, index: Int, range: Int): List<StarObject> {
        val biggerList = list + list + list
        return biggerList.subList(index + list.size - range, index + list.size + range + 1)
    }

    fun valid(list: List<StarObject>): Boolean {
        val inferredExpert = list.size == 18
        if (!inferredExpert) check(list.size == 12) { "Unknown list size: ${list.size}" }

        // PlanetX not adjacent to a Dwarf Planet
        val planetX = list.indexOf(StarObject.PlanetX)
        if (getWithin(list, planetX, range = 1).contains(StarObject.DwarfPlanet)) return false

        // Comets only in sectors 2 3 5 7 11 13 17
        val comet1 = list.indexOfFirst { it == StarObject.Comet }
        val comet2 = list.indexOfLast { it == StarObject.Comet }
        if (comet1 !in validCometLocations || comet2 !in validCometLocations) return false

        // Asteroid must be adjacent to at least one other asteroid (either pairs, or all 4 together)
        val asteroidIndices = list.mapIndexedNotNull { index, starObject ->
            if (starObject == StarObject.Asteroid) index else null
        }
        if (!asteroidIndicesMatch(asteroidIndices, inferredExpert)) return false

        // Gas Clouds adjacent to at least one truly empty
        val gas1 = list.indexOfFirst { it == StarObject.GasCloud }
        val gas2 = list.indexOfFirst { it == StarObject.GasCloud }
        if (!getWithin(list, gas1, range = 1).contains(StarObject.TrulyEmpty)) return false
        if (!getWithin(list, gas2, range = 1).contains(StarObject.TrulyEmpty)) return false

        // Dwarf Planets in a band of 6
        if (!inferredExpert) return true // Only one Dwarf Planet, condition already checked.

        val dwarfPlanets = list.mapIndexedNotNull { index, starObject ->
            if (starObject == StarObject.DwarfPlanet) index else null
        }
        return biggestDistanceExpert(dwarfPlanets) == 5
    }

    private fun biggestDistanceExpert(sectors: List<Int>): Int {
        var max = 0
        for (i in sectors) {
            for (j in sectors) {
                val distance = sectorDistanceExpert(i, j)
                if (distance > max) max = distance
            }
        }
        return max
    }
    fun List<StarObject>.wrapGet(index: Int): StarObject = this[index % size]

    private fun MutableList<StarObject>.setObjects(obj: StarObject, index: Int, addIndex: List<Int>): Boolean {
        for (i in indices) {
            if (this[i] == obj) this[i] = StarObject.TrulyEmpty
        }
        if (addIndex.any { this[(index + it) % size] != StarObject.TrulyEmpty }) return false

        for (i in addIndex) {
            this[(index + i) % size] = obj
        }
        return true
    }

    class Search(expert: Boolean) {
        val range = 0 until 18
        val size = range.count()

        private val dwarfVariants = listOf(
            listOf(0, 3, 4, 5),
            listOf(0, 2, 4, 5),
            listOf(0, 2, 3, 5),
            listOf(0, 1, 4, 5),
            listOf(0, 1, 3, 5),
            listOf(0, 1, 2, 5),
        )
        private suspend fun dwarfPlanets(list: MutableList<StarObject>, next: suspend (MutableList<StarObject>) -> Unit) {
            for (firstDwarf in range) {
                for (dwarfOrder in dwarfVariants) {
                    if (list.setObjects(StarObject.DwarfPlanet, firstDwarf, dwarfOrder)) next.invoke(list)
                }
            }
        }

        private suspend fun asteroids(list: MutableList<StarObject>, next: suspend (MutableList<StarObject>) -> Unit) {
            for (firstAsteroidPair in range) {
                if (list[firstAsteroidPair] == StarObject.TrulyEmpty && list.wrapGet(firstAsteroidPair + 1) == StarObject.TrulyEmpty) {
                    for (secondAsteroidPair in range) {
                        if (list[secondAsteroidPair] == StarObject.TrulyEmpty && list.wrapGet(secondAsteroidPair + 1) == StarObject.TrulyEmpty) {
                            val asteroids = setOf(firstAsteroidPair, firstAsteroidPair + 1, secondAsteroidPair, secondAsteroidPair + 1)
                            if (asteroids.size == 4) {
                                if (list.setObjects(StarObject.Asteroid, 0, asteroids.toList())) next.invoke(list)
                            }
                        }
                    }
                }
            }
        }

        private suspend fun comeets(list: MutableList<StarObject>, next: suspend (MutableList<StarObject>) -> Unit) {
            for (firstComet in validCometLocations) {
                if (list[firstComet] == StarObject.TrulyEmpty) {
                    for (secondComet in validCometLocations.minus(firstComet)) {
                        if (list[secondComet] == StarObject.TrulyEmpty) {
                            val comets = listOf(firstComet, secondComet)
                            if (list.setObjects(StarObject.Comet, 0, comets)) next.invoke(list)
                        }
                    }
                }
            }
        }

        private suspend fun gasClouds(list: MutableList<StarObject>, next: suspend (MutableList<StarObject>) -> Unit) {
            val remaining = list.mapIndexedNotNull { index, starObject ->
                if (starObject == StarObject.TrulyEmpty) index else null
            }

            // 2 Gas Clouds
            for (gasCloud1 in remaining) {
                if (getWithin(list, gasCloud1, 1).count { it == StarObject.TrulyEmpty } >= 2) {
                    for (gasCloud2 in remaining.minus(gasCloud1)) {
                        if (getWithin(list, gasCloud2, 1).count { it == StarObject.TrulyEmpty } >= 2) {
                            val gasClouds = listOf(gasCloud1, gasCloud2)
                            if (list.setObjects(StarObject.GasCloud, 0, gasClouds)) next.invoke(list)
                        }
                    }
                }
            }
        }

        private suspend fun planetX(list: MutableList<StarObject>, next: suspend (MutableList<StarObject>) -> Unit) {
            val remaining = list.mapIndexedNotNull { index, starObject ->
                if (starObject == StarObject.TrulyEmpty) index else null
            }

            for (planetX in remaining) {
                if (list.setObjects(StarObject.PlanetX, planetX, LIST_OF_ZERO)) next.invoke(list)
            }
        }


        private fun iterateRaw(): Flow<List<StarObject>> {

            return flow {
                // Dwarfs, 2 sets of asteroids, gas clouds, comets, planetX
                val it1 = range.map { StarObject.TrulyEmpty }.toMutableList()
                val mutableList = range.map { StarObject.TrulyEmpty }.toMutableList()

                for (firstDwarf in range) {
                    for (dwarfOrder in dwarfVariants) {
                        mutableList.setObjects(StarObject.DwarfPlanet, firstDwarf, dwarfOrder)

                        for (firstAsteroidPair in range) {
                            if (mutableList[firstAsteroidPair] == StarObject.TrulyEmpty && mutableList.wrapGet(firstAsteroidPair + 1) == StarObject.TrulyEmpty) {

                                for (secondAsteroidPair in range) {
                                    if (mutableList[secondAsteroidPair] == StarObject.TrulyEmpty && mutableList.wrapGet(secondAsteroidPair + 1) == StarObject.TrulyEmpty) {
                                        val asteroids = setOf(firstAsteroidPair, firstAsteroidPair + 1, secondAsteroidPair, secondAsteroidPair + 1)
                                        if (asteroids.size == 4) {
                                            mutableList.setObjects(StarObject.Asteroid, 0, asteroids.toList())
                                            placeRest(mutableList) {
                                                emit(it)
                                            }
                                        }
                                        // Remaining: Place Comets, PlanetX, Gas Clouds
                                    }
                                }
                            }
                        }


                    }
                }
            }
        }


        private suspend fun placeRest(list: MutableList<StarObject>, completed: suspend (List<StarObject>) -> Unit) {
            // 2 Comets
            for (firstComet in validCometLocations) {
                if (list[firstComet] == StarObject.TrulyEmpty) {
                    for (secondComet in validCometLocations.minus(firstComet)) {
                        if (list[secondComet] == StarObject.TrulyEmpty) {
                            val comets = listOf(firstComet, secondComet)
                            list.setObjects(StarObject.Comet, 0, comets)

                            val remaining = list.mapIndexedNotNull { index, starObject ->
                                if (starObject == StarObject.TrulyEmpty) index else null
                            }

                            // PlanetX
                            for (planetX in remaining) {
                                list.setObjects(StarObject.PlanetX, planetX, LIST_OF_ZERO)
                                val remainingAfterX = remaining.minus(planetX)

                                // 2 Gas Clouds
                                for (gasCloud1 in remainingAfterX) {
                                    if (getWithin(list, gasCloud1, 1).count { it == StarObject.TrulyEmpty } >= 2) {
                                        for (gasCloud2 in remainingAfterX.minus(gasCloud1)) {
                                            if (getWithin(list, gasCloud2, 1).count { it == StarObject.TrulyEmpty } >= 2) {
                                                val gasClouds = listOf(gasCloud1, gasCloud2)
                                                list.setObjects(StarObject.GasCloud, 0, gasClouds)

                                                completed.invoke(list.toList())
                                            }
                                        }
                                    }
                                }

                            }
                        }
                    }
                }
            }
        }

        fun iterateUse() = iterateRaw().distinctUntilChanged().filter { valid(it) }




        // 2 494 800 in standard
        // 23 156 733 600 in expert
        /*
        *
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
        *
        *
        *
        *
        *
        *
        * Question: Is the probability of PlanetX equal on all sectors? Does it depend on what you place first?
        *
        */

        fun generator(expert: Boolean) = flow {
            while (true) {
                emit(random(expert))
                yield()
            }
        }.filter { valid(it) }

        suspend fun f() {
            val all = iterateUse().toList()
            println(all.size)

            val combinations = all.toMutableSet()
            println(combinations.size)

            generator(true).runningFold(combinations.toSet()) { accumulator: Set<List<StarObject>>, value: List<StarObject> ->
                accumulator.plus(element = value)
            }.conflate().collect {
                println(it.size)
                delay(500)
            }
            return


            generator(true).filter { it !in combinations }.collect {
                if (combinations.add(it)) println(combinations.size)
                // println(it)

            }
            return

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
