package net.zomis.games.impl.planetx

import net.zomis.GreedyIterator
import kotlin.math.min

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

    fun bestActionForTheory(visibleSectors: SectorRange) {
        TODO()
    }

    fun surveyResultPossibilities(sectorRange: SectorRange, surveyTarget: PlanetX.StarObject): List<Int> {
        val rangeSize = sectorRange.count()
        val max = min(rangeSize, game.countOf(surveyTarget))
        val possibleSurveyResults = 0..max
        return possibleSurveyResults.map { result ->
            possibilities.count { it.survey(sectorRange, surveyTarget) == result }
        }
    }

    fun bestActionForPossibilities(visibleSectors: SectorRange) {
        val surveyable = PlanetX.surveyable
        val total = size.toDouble()
        val sectors = visibleSectors.toList()

        var best = total
        var bestParams = listOf<Any>()
        val bestAverageProbabilities = GreedyIterator<List<Any>>(descending = true)
        val worstCaseScenario = GreedyIterator<List<Any>>(descending = true)

        for (startIndex in sectors.indices) {
            val startSector = sectors[startIndex]
            for (endIndex in sectors.indices.filter { it >= startIndex }) {
                val endSector = sectors[endIndex]
                val range = game.sectorRange(sectors[startIndex], sectors[endIndex])

                for (surveyTarget in surveyable) {
                    // Surveys for comets must start and end in a sector that can contain comets
                    if (surveyTarget == PlanetX.StarObject.Comet) {
                        if (!game.canHaveComet(startSector)) continue
                        if (!game.canHaveComet(endSector)) continue
                    }

                    // TODO: Consider checking "worst case scenario" instead
                    val surveyResults = surveyResultPossibilities(range, surveyTarget)
                    val surveyResultsProbabilities = surveyResults.map { it / total }
                    val surveyResultsExpected = surveyResults.map {
                        val probability = it / total
                        probability * it
                    }
                    val expected = surveyResultsExpected.filter { it > 0 }.average()
                    val params = listOf(sectors[startIndex], sectors[endIndex], surveyTarget)
                    println("Eval: $expected for $params. $surveyResultsExpected $surveyResults $surveyResultsProbabilities")
                    bestAverageProbabilities.next(expected) { params }
                    worstCaseScenario.next(surveyResults.max().toDouble()) { params }

                    if (expected < best) {
                        best = expected
                        bestParams = params
                    }
                }
            }
        }
        println("Best: $best with $bestParams")
        println("Greedy Average: ${bestAverageProbabilities.bestPair()} ${bestAverageProbabilities.getBest()}")
        println("Greedy Worst: ${worstCaseScenario.bestPair()} ${worstCaseScenario.getBest()}")

        // Survey: 1-3 = 4 time units, 4-6 = 3 time units, 7-9 = 2 time units
        // Target: 4 time unit
        // Research: 1 time unit. Unknown result
    }

    fun botTheory(sector1: Int, sector2: Int? = null) {
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
    data class Theory(val sector: Int, val starObject: PlanetX.StarObject) {
        fun isValid(): Boolean = starObject in PlanetX.possibleTheories
    }

    fun bestTheories(excludeSectors: List<Int> = emptyList()) {
        // Get possible theories
        val possibleTheories = probabilitiesBySector().flatMapIndexed { index: Int, map: Map<PlanetX.StarObject, Double> ->
            map.entries.map { Theory(index + 1, it.key) to it.value }
        }.filter { it.first.isValid() }.filter { it.first.sector !in excludeSectors }.sortedByDescending { it.second }

        possibleTheories.take(2).forEach {
            println("Theory ${it.first}: ${it.second}")
        }
    }

    fun surveyResult(sectors: SectorRange, target: PlanetX.StarObject, result: Int) {
        this.filter { it.survey(sectors, target) == result }
    }

}