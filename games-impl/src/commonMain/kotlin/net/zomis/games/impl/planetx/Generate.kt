package net.zomis.games.impl.planetx

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.yield

class Generate(private val game: StarGame) {
    private val range = game.range
    private val dwarfVariants = listOf(
        listOf(0, 3, 4, 5),
        listOf(0, 2, 4, 5),
        listOf(0, 2, 3, 5),
        listOf(0, 1, 4, 5),
        listOf(0, 1, 3, 5),
        listOf(0, 1, 2, 5),
    )
    fun emptySpace() = StarMap(game = game, list = range.map { PlanetX.StarObject.TrulyEmpty }.toMutableList())
    private suspend fun dwarfPlanets(list: StarMap, next: suspend (StarMap) -> Unit) {
        for (firstDwarf in range) {
            for (dwarfOrder in dwarfVariants) {
                if (list.setObjects(PlanetX.StarObject.DwarfPlanet, firstDwarf, dwarfOrder)) {
                    next.invoke(list)
                    list.clearOf(PlanetX.StarObject.DwarfPlanet)
                }
            }
        }
    }

    private suspend fun asteroids(list: StarMap, next: suspend (StarMap) -> Unit) {
        for (firstAsteroidPair in range) {
            if (list.available(firstAsteroidPair, PlanetX.StarObject.Asteroid) && list.available(firstAsteroidPair + 1, PlanetX.StarObject.Asteroid)) {
                for (secondAsteroidPair in range.filter { it > firstAsteroidPair }) {
                    if (list.available(secondAsteroidPair, PlanetX.StarObject.Asteroid) && list.available(secondAsteroidPair + 1, PlanetX.StarObject.Asteroid)) {
                        val asteroids = setOf(firstAsteroidPair, (firstAsteroidPair + 1) % list.size, secondAsteroidPair, (secondAsteroidPair + 1) % list.size)
                        if (asteroids.size == 4) {
                            if (list.setObjects(PlanetX.StarObject.Asteroid, 0, asteroids.toList())) {
                                next.invoke(list)
                                list.clearOf(PlanetX.StarObject.Asteroid)
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun comets(list: StarMap, next: suspend (StarMap) -> Unit) {
        for (firstComet in game.validCometLocations) {
            if (list[firstComet] == PlanetX.StarObject.TrulyEmpty || list[firstComet] == PlanetX.StarObject.Comet) {
                for (secondComet in game.validCometLocations.filter { it > firstComet }) {
                    if (list[secondComet] == PlanetX.StarObject.TrulyEmpty || list[secondComet] == PlanetX.StarObject.Comet) {
                        val comets = listOf(firstComet, secondComet)
                        if (list.setObjects(PlanetX.StarObject.Comet, 0, comets)) {
                            next.invoke(list)
                            list.clearOf(PlanetX.StarObject.Comet)
                        }
                    }
                }
            }
        }
    }

    private suspend fun gasClouds(list: StarMap, next: suspend (StarMap) -> Unit) {
        val remaining = list.allIndicesOf(PlanetX.StarObject.TrulyEmpty)

        // 2 Gas Clouds
        for (gasCloud1 in remaining) {
            list.clearOf(PlanetX.StarObject.GasCloud)
            val emptySpacesForGasCloud1 = list.getWithin(gasCloud1, 1).count { it == PlanetX.StarObject.TrulyEmpty }
            if (emptySpacesForGasCloud1 >= 2) {
                list[gasCloud1] = PlanetX.StarObject.GasCloud
                // TODO: 1001 1010 0101 0110. There's one too many being emitted here, because we're blocking the empty space that gasCloud1 reserved.
                for (gasCloud2 in remaining.minus(gasCloud1).filter { it > gasCloud1 }) {
                    if (list.getWithin(gasCloud2, 1).count { it == PlanetX.StarObject.TrulyEmpty } >= 2) {
                        val gasClouds = listOf(gasCloud1, gasCloud2)
                        if (list.setObjects(PlanetX.StarObject.GasCloud, 0, gasClouds)) {
                            next.invoke(list)
                            list.clearOf(PlanetX.StarObject.GasCloud)
                        }
                    }
                }
                list[gasCloud1] = PlanetX.StarObject.TrulyEmpty
            }
        }
    }

    private suspend fun planetX(list: StarMap, next: suspend (StarMap) -> Unit) {
        val remaining = list.emptyIndices()

        for (planetX in remaining) {
            if (list[planetX] == PlanetX.StarObject.TrulyEmpty) {
                list[planetX] = PlanetX.StarObject.PlanetX
                next.invoke(list)
                list[planetX] = PlanetX.StarObject.TrulyEmpty
            }
        }
    }

    private fun iterateRaw(): Flow<StarMap> {
        return flow {
            // Dwarfs, 2 sets of asteroids, gas clouds, comets, planetX
            val it1 = emptySpace()
            dwarfPlanets(it1) { it2 ->
                asteroids(it2) { it3 ->
                    comets(it3) { it4 ->
                        planetX(it4) { it5 ->
                            gasClouds(it5) { final ->
                                emit(final.copy())
                            }
                        }
                    }
                }
            }
        }
    }

    fun iterateUse() = iterateRaw().distinctUntilChanged().filter { it.valid() }

    fun generator() = flow {
        while (true) {
            emit(game.random())
            yield()
        }
    }.filter { it.valid() }

    fun <T> Flow<T>.collectCombinations(): Flow<Set<T>> {
        return this.runningFold(setOf()) { accumulator: Set<T>, value: T ->
            accumulator.plus(element = value)
        }
    }
    suspend fun <T> Flow<Set<T>>.finalCount(): Int = this.last().size

    suspend fun combinationCount(name: String, debug: Boolean = false, block: suspend FlowCollector<StarMap>.() -> Unit) {
        flow {
            block.invoke(this)
        }
            .onEach { if (debug) println(it) }
            .map { it.copy() }
            .collectCombinations().finalCount().let { println("$name: $it") }
    }

    suspend fun test() {
        val generator = this
        val all = generator.iterateUse().toList()
        println("All: " + all.size)

        val combinations = all.toMutableSet()
        println("Combinations: " + combinations.size)

        generator.combinationCount("Asteroids") {
            generator.asteroids(emptySpace()) { emit(it) }
        }
        generator.combinationCount("Dwarf Planets", debug = false) { // 16 * 6 = 108
            dwarfPlanets(emptySpace()) { emit(it) }
        }
        generator.combinationCount("Comets") { // 7 nCr 2 = 21
            comets(emptySpace()) { emit(it) }
        }
        generator.combinationCount("PlanetX") { // 18
            planetX(emptySpace()) { emit(it) }
        }
        generator.combinationCount("Gas Clouds", debug = true) { // 4
            val space = emptySpace()
            space.setObjects(PlanetX.StarObject.Asteroid, 0, (1..14).toList())
            gasClouds(space) { emit(it) }
        }
//            return
//            estimateTotals(expert = true)
        println()
        println("Generated but not iterated:")

        generator.generator().filter {
            combinations.add(it)
        }.take(5).collect {
            println(it)
        }
    }

    private suspend fun estimateTotals() {
        generator().collectCombinations().conflate().map { it.size }.distinctUntilChanged().collect {
            println(it)
            delay(1000)
        }
    }

}