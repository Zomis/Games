package net.zomis.games.impl.planetx

import kotlinx.coroutines.flow.*

object PlanetX {

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

    val surveyable = StarObject.values().filter { it != StarObject.PlanetX }.toSet()

    val possibleTheories = setOf(
        StarObject.Comet, StarObject.Asteroid, StarObject.DwarfPlanet, StarObject.GasCloud,
    )

    fun cardAnalyze() {
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
        TODO()
    }

    suspend fun playAround() {
        // Theories after 3, 6, 9, 12, 15, 18. X Conference after 7 and 16
        // Simple mode: 3, 6, 9, 12. X Conference after 10.
        // TODO: What conclusions can be drawn from opponent actions?

        val game = StarGame(expert = true)
        val search = game.startSearch()
        search.printProbabilities("ALL")
//        search.surveyResult(game.sectorRange(1, 9), StarObject.DwarfPlanet, 0)

        search.bestActionForPossibilities(game.visibleSkyFrom(1))
        search.printProbabilities("After another turn")
        search.bestTheories(listOf(12, 15, 14, 17, 16, 3, 1, 2, 18))
        println("Remaining: ${search.size}")

        search.atLeastOne(StarObject.Comet).isWithin(3).sectorsOf(StarObject.DwarfPlanet)
        search.no(StarObject.Comet).isDirectlyOpposite(StarObject.DwarfPlanet)
        search.all(StarObject.Asteroid).areWithinBandOf(7)
        search.all(StarObject.Asteroid).areConsecutive()
        search.no(StarObject.Asteroid).isWithin(3).sectorsOfAnother()
        search.all(StarObject.Asteroid).isWithin(7).sectorsOfAnother()

        return
        search.filter {
            // Research: At least one Comet is adjacent to 1 Dwarf Planet
            it.allIndicesOf(StarObject.Comet).any { i ->
                it.allIndicesOf(StarObject.DwarfPlanet).any { j -> game.sectorDistance(i, j) == 1 }
            }
        }
        search.filter {
            // Research: All comets within 2 sectors of an asteroid
            it.allIndicesOf(StarObject.Comet).all { i ->
                it.allIndicesOf(StarObject.Asteroid).any { j -> game.sectorDistance(i, j) <= 2 }
            }
        }
        /*
        * Research / Conferences:
        * - No A not within x of B
        * - At least one A is within x of B
        * - (No / At least x / At most x? / All)
        * - is within y sectors of / is in band of y / is directly opposite
        * - No A is within x sectors of another A
        * - All A are in consecutive sectors
        */

        // Conference: Planet X is within two sectors of an asteroid
        search.filter {
            val planetX = it.indexOfPlanetX()
            it.allIndicesOf(StarObject.Asteroid).any { a -> game.sectorDistance(a, planetX) <= 2 }
        }
        search.filter {
            // Research: No Dwarf Planet within 3 sectors of a Gas Cloud
            it.allIndicesOf(StarObject.DwarfPlanet).none { i ->
                it.allIndicesOf(StarObject.GasCloud).any { j -> game.sectorDistance(i, j) <= 3 }
            }
        }
    }
}
