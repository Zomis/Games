package net.zomis.games.impl.planetx

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.yield
import net.zomis.games.cards.CardZone
import kotlin.math.abs

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
        val gas2 = list.indexOfLast { it == StarObject.GasCloud }
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
    fun List<StarObject>.available(index: Int, obj: StarObject): Boolean = wrapGet(index).let { it == StarObject.TrulyEmpty || it == obj }
    private fun MutableList<StarObject>.clearOf(obj: StarObject) {
        for (i in indices) {
            if (this[i] == obj) this[i] = StarObject.TrulyEmpty
        }
    }

    private fun MutableList<StarObject>.setObjects(obj: StarObject, index: Int, addIndex: List<Int>): Boolean {
        for (i in indices) {
            if (this[i] == obj) this[i] = StarObject.TrulyEmpty
        }
        if (addIndex.any { this[(index + it) % size] != StarObject.TrulyEmpty }) {
            println("Could not setObjects: $obj $index $addIndex. $this")
            return false
        }

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
                    if (list.setObjects(StarObject.DwarfPlanet, firstDwarf, dwarfOrder)) {
                        next.invoke(list)
                        list.clearOf(StarObject.DwarfPlanet)
                    }
                }
            }
        }

        private suspend fun asteroids(list: MutableList<StarObject>, next: suspend (MutableList<StarObject>) -> Unit) {
            for (firstAsteroidPair in range) {
                if (list.available(firstAsteroidPair, StarObject.Asteroid) && list.available(firstAsteroidPair + 1, StarObject.Asteroid)) {
                    for (secondAsteroidPair in range.filter { it > firstAsteroidPair }) {
                        if (list.available(secondAsteroidPair, StarObject.Asteroid) && list.available(secondAsteroidPair + 1, StarObject.Asteroid)) {
                            val asteroids = setOf(firstAsteroidPair, (firstAsteroidPair + 1) % size, secondAsteroidPair, (secondAsteroidPair + 1) % size)
                            if (asteroids.size == 4) {
                                if (list.setObjects(StarObject.Asteroid, 0, asteroids.toList())) {
                                    next.invoke(list)
                                    list.clearOf(StarObject.Asteroid)
                                }
                            }
                        }
                    }
                }
            }
        }

        private suspend fun comets(list: MutableList<StarObject>, next: suspend (MutableList<StarObject>) -> Unit) {
            for (firstComet in validCometLocations) {
                if (list[firstComet] == StarObject.TrulyEmpty || list[firstComet] == StarObject.Comet) {
                    for (secondComet in validCometLocations.filter { it > firstComet }) {
                        if (list[secondComet] == StarObject.TrulyEmpty || list[secondComet] == StarObject.Comet) {
                            val comets = listOf(firstComet, secondComet)
                            if (list.setObjects(StarObject.Comet, 0, comets)) {
                                next.invoke(list)
                                list.clearOf(StarObject.Comet)
                            }
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
                list.clearOf(StarObject.GasCloud)
                val emptySpacesForGasCloud1 = getWithin(list, gasCloud1, 1).count { it == StarObject.TrulyEmpty }
                if (emptySpacesForGasCloud1 >= 2) {
                    list[gasCloud1] = StarObject.GasCloud
                    // TODO: 1001 1010 0101 0110. There's one too many being emitted here, because we're blocking the empty space that gasCloud1 reserved.
                    for (gasCloud2 in remaining.minus(gasCloud1).filter { it > gasCloud1 }) {
                        if (getWithin(list, gasCloud2, 1).count { it == StarObject.TrulyEmpty } >= 2) {
                            val gasClouds = listOf(gasCloud1, gasCloud2)
                            if (list.setObjects(StarObject.GasCloud, 0, gasClouds)) {
                                next.invoke(list)
                                list.clearOf(StarObject.GasCloud)
                            }
                        }
                    }
                    list[gasCloud1] = StarObject.TrulyEmpty
                }
            }
        }

        private suspend fun planetX(list: MutableList<StarObject>, next: suspend (MutableList<StarObject>) -> Unit) {
            val remaining = list.mapIndexedNotNull { index, starObject ->
                if (starObject == StarObject.TrulyEmpty) index else null
            }

            for (planetX in remaining) {
                if (list[planetX] == StarObject.TrulyEmpty) {
                    list[planetX] = StarObject.PlanetX
                    next.invoke(list)
                    list[planetX] = StarObject.TrulyEmpty
                }
            }
        }
        fun emptySpace() = range.map { StarObject.TrulyEmpty }.toMutableList()

        private fun iterateRaw(): Flow<List<StarObject>> {
            return flow {
                // Dwarfs, 2 sets of asteroids, gas clouds, comets, planetX
                val it1 = emptySpace()
                dwarfPlanets(it1) { it2 ->
                    asteroids(it2) { it3 ->
                        comets(it3) { it4 ->
                            planetX(it4) { it5 ->
                                gasClouds(it5) { final ->
                                    emit(final.toList())
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

        fun <T> Flow<T>.collectCombinations(): Flow<Set<T>> {
            return this.runningFold(setOf()) { accumulator: Set<T>, value: T ->
                accumulator.plus(element = value)
            }
        }
        suspend fun <T> Flow<Set<T>>.finalCount(): Int = this.last().size

        suspend fun combinationCount(name: String, debug: Boolean = false, block: suspend FlowCollector<List<StarObject>>.() -> Unit) {
            flow<List<StarObject>> {
                block.invoke(this)
            }
                .onEach { if (debug) println(it) }
                .map { it.toList() }
                .collectCombinations().finalCount().let { println("$name: $it") }
        }

        suspend fun f() {
            val list = "TrulyEmpty, GasCloud, Comet, Asteroid, Asteroid, DwarfPlanet, TrulyEmpty, TrulyEmpty, DwarfPlanet, DwarfPlanet, DwarfPlanet, TrulyEmpty, Asteroid, Asteroid, PlanetX, GasCloud, Comet, TrulyEmpty"
                .split(", ").map { StarObject.valueOf(it) }
            println(valid(list))


            val all = iterateUse().toList()
            println("All: " + all.size)

            val combinations = all.toMutableSet()
            println("Combinations: " + combinations.size)

            combinationCount("Asteroids") {
                asteroids(emptySpace()) { emit(it) }
            }
            combinationCount("Dwarf Planets", debug = false) { // 16 * 6 = 108
                dwarfPlanets(emptySpace()) { emit(it) }
            }
            combinationCount("Comets") { // 7 nCr 2 = 21
                comets(emptySpace()) { emit(it) }
            }
            combinationCount("PlanetX") { // 18
                planetX(emptySpace()) { emit(it) }
            }
            combinationCount("Gas Clouds", debug = true) { // 4
                val space = emptySpace()
                space.setObjects(StarObject.Asteroid, 0, (1..14).toList())
                gasClouds(space) { emit(it) }
            }
//            return
//            estimateTotals(expert = true)
            println()
            println("Generated but not iterated:")

            generator(expert = true).filter {
                combinations.add(it)
            }.take(5).collect {
                println(it)
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

        private suspend fun lookFor(lookingFor: List<StarObject>) {
            var bestInt: Long = 0L
            var best = listOf<StarObject>()
            iterateUse().collect { list ->
                val matches = list.indices.sumOf {
                    if (list[it] != lookingFor[it]) 0L
                    else when (list[it]) {
                        StarObject.Comet -> 8
                        StarObject.Asteroid -> 9
                        StarObject.DwarfPlanet -> 10
                        StarObject.GasCloud -> 5
                        StarObject.PlanetX -> 7
                        StarObject.TrulyEmpty -> 1
                    }
                }
                if (matches > bestInt) {
                    bestInt = matches
                    best = list.toList()
                }
            }
            println(bestInt)
            println(best)
        }

        private suspend fun estimateTotals(expert: Boolean) {
            generator(expert).collectCombinations().conflate().map { it.size }.distinctUntilChanged().collect {
                println(it)
                delay(1000)
            }
        }
    }
}

fun <E> MutableList<E>.allIndicesOf(obj: E): List<Int> {
    return this.mapIndexedNotNull { index, e ->
        if (e == obj) index else null
    }
}
