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
        val game = StarGame(expert = true)
        val search = game.startSearch()
        search.printProbabilities("ALL")
        search.exclude(
            2 to StarObject.GasCloud,
            3 to StarObject.DwarfPlanet,
            7 to StarObject.GasCloud,
            13 to StarObject.Asteroid,
        )

        // Start info
        search.filter {
            it.sector(2) != StarObject.GasCloud && it.sector(3) != StarObject.DwarfPlanet &&
                    it.sector(7) != StarObject.GasCloud && it.sector(13) != StarObject.Asteroid
        }

        search.printProbabilities("After start")

        search.filter {
            it.survey(game.sectorRange(2, 7), StarObject.Comet) == 1
        }

        search.filter { it.survey(2..7, StarObject.Comet) == 1 }
        search.printProbabilities("Action")

        // Bot theory 9 10
        search.filter { it.sector(9) in possibleTheories && it.sector(10) in possibleTheories }
        search.sectorsCanHaveTheories(9, 10)

        // Theories after 3, 6, 9, 12, 15, 18. X Conference after 7 and 16
        // Simple mode: 3, 6, 9, 12. X Conference after 10.

        search.filter {
            // Research: At least one Comet is adjacent to 1 Dwarf Planet
            it.allIndicesOf(StarObject.Comet).any { i ->
                it.allIndicesOf(StarObject.DwarfPlanet).any { j -> game.sectorDistance(i, j) == 1 }
            }
        }
        search.printProbabilities("Action")

        search.filter { it.survey(5..12, StarObject.Asteroid) == 2 }

        // Bot theory 11
        search.filter { it.sector(11) in possibleTheories }
        search.printProbabilities("Action")

        search.filter {
            // Research: All comets within 2 sectors of an asteroid
            it.allIndicesOf(StarObject.Comet).all { i ->
                it.allIndicesOf(StarObject.Asteroid).any { j -> game.sectorDistance(i, j) <= 2 }
            }
        }
        /*
        * Research:
        * - No A not within x of B
        * - At least one A is within x of B
        * - (No / At least x / At most x? / All)
        * - is within y sectors of / is in band of y / is directly opposite
        * - No A is within x sectors of another A
        * */


        // Conference: Planet X is within two sectors of an asteroid
        search.filter {
            val planetX = it.indexOfPlanetX()
            it.allIndicesOf(StarObject.Asteroid).any { a -> game.sectorDistance(a, planetX) <= 2 }
        }
        search.printProbabilities("Action")

        search.filter { it.survey(8..14, StarObject.DwarfPlanet) == 4 }

        // Bot theory 13, 12
        search.filter { it.sector(13) in possibleTheories && it.sector(12) in possibleTheories }

        // Reveal bot theories for 9 and 10
        search.filter { it.sector(9) == StarObject.Asteroid && it.sector(10) == StarObject.Asteroid }
        search.printProbabilities("Action")

        search.filter { it.survey(12..18, StarObject.TrulyEmpty) == 2 }
        search.filter {
            // Research: No Dwarf Planet within 3 sectors of a Gas Cloud
            it.allIndicesOf(StarObject.DwarfPlanet).none { i ->
                it.allIndicesOf(StarObject.GasCloud).any { j -> game.sectorDistance(i, j) <= 3 }
            }
        }
        search.printProbabilities("Action")

        // Bot theory 8 15
        search.filter { it.sector(8) in possibleTheories && it.sector(15) in possibleTheories }
        // Reveal bot theory for 11
        search.filter { it.sector(11) == StarObject.DwarfPlanet }

        // Survey asteroid 13-1, result 2
        search.filter { it.survey(game.sectorRange(13, 1), StarObject.Asteroid) == 2 }
        search.printProbabilities("Action")

        // Survey asteroid 17-5, result 0
        search.filter { it.survey(game.sectorRange(17, 5), StarObject.Asteroid) == 0 }

        // Bot theory 16 7
        // Reveal bot theories for 12 and 13
        search.filter { it.sector(16) in possibleTheories && it.sector(7) in possibleTheories }
        search.filter { it.sector(12) == StarObject.DwarfPlanet && it.sector(13) == StarObject.DwarfPlanet }
        search.printProbabilities("Action")

        // Survey gas cloud 17-2, result 0
        search.filter { it.survey(game.sectorRange(17, 2), StarObject.GasCloud) == 0 }

        // Bot theory 17
        // Reveal bot theories for 8 and 15
        search.filter { it.sector(17) in possibleTheories }
        search.filter { it.sector(8) == StarObject.DwarfPlanet && it.sector(15) == StarObject.Asteroid }

        search.printProbabilities("Action")

        // TODO: Filter on what I researched and all my actions. Maybe also consider opponent actions. (That is how I found planet X after all)
    }
}
