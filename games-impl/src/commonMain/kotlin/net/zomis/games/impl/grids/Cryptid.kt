package net.zomis.games.impl.grids

import net.zomis.games.api.GamesApi
import net.zomis.games.cards.probabilities.Combinatorics
import net.zomis.games.common.next
import net.zomis.games.components.Point
import net.zomis.games.components.grids.HexGrid
import net.zomis.games.components.grids.HexPoint
import kotlin.math.abs

object Cryptid {
    fun test() {
        val data = CryptidData.MapData(
            listOf(1,2,5,3,4,6),
            listOf(false, false, false, false, false, true),
            listOf(
                CryptidData.StructureView(Structure(StructureType.Shack, StructureColor.Blue), Point(4, 3)),
                CryptidData.StructureView(Structure(StructureType.Shack, StructureColor.Green), Point(2, 3)),
                CryptidData.StructureView(Structure(StructureType.Shack, StructureColor.White), Point(4, 8)),
                CryptidData.StructureView(Structure(StructureType.Stone, StructureColor.Blue), Point(7, 1)),
                CryptidData.StructureView(Structure(StructureType.Stone, StructureColor.Green), Point(0, 6)),
                CryptidData.StructureView(Structure(StructureType.Stone, StructureColor.White), Point(10, 5)),
            )
        )
        val m = Model(data, data.createHexGrid(), listOf(
            Animal.Bear.clue(),
            StructureColor.White.clue(),
            Terrain.Forest.or(Terrain.Desert),
            StructureColor.Green.clue(),
        ), "")
        println(m.answer())
    }

    data class Structure(val type: StructureType, val color: StructureColor)
    data class CryptidField(
        val terrain: Terrain,
        val animal: Animal?,
        val structure: Structure?,
        val discs: MutableSet<Int> = mutableSetOf(),
        var cube: Int? = null
    )

    class Deduction(val playerIndex: Int, val advanced: Boolean) {
        private val possibleClues = allClues(advanced).toMutableList()
        val possible get() = possibleClues.toList()

        constructor(playerIndex: Int, advanced: Boolean, possibleClues: List<Clue>): this(playerIndex, advanced) {
            this.possibleClues.retainAll(possibleClues)
        }

        fun update(model: Model) {
            val cubes = model.hexGrid.values().filter {
                it.value.cube == playerIndex
            }
            val discs = model.hexGrid.values().filter {
                it.value.discs.contains(playerIndex)
            }
            possibleClues.removeAll { clue ->
                cubes.any { model.fulfills(clue, it.hex) }
            }
            possibleClues.removeAll { clue ->
                discs.any { !model.fulfills(clue, it.hex) }
            }
        }

        override fun toString(): String = possibleClues.toString()
    }

    class Deductions(private val model: Model, val playerIndex: Int) {
        val deductions = model.players.indices.minus(playerIndex).associateWith { Deduction(it, model.advanced) }
        val selfDeduction = Deduction(playerIndex, model.advanced)
        val ownClue = model.players[playerIndex]

        private fun deductions(ownClueKnown: Boolean): Map<Int, Deduction> {
            return if (ownClueKnown) {
                deductions.plus(playerIndex to Deduction(playerIndex, model.advanced, listOf(ownClue)))
            } else {
                deductions.plus(playerIndex to selfDeduction)
            }
        }

        fun possibleHexes(considerOwnClue: Boolean): Map<HexPoint, Int> {
            val options = options(considerOwnClue)
            val possibilities = Combinatorics.combinations(options)
            val deductions = deductions(considerOwnClue)
            val remainingHexes = model.hexGrid.values()
                .filter { model.canPlaceCube(it.hex) }
//                .filter { !considerOwnClue || model.fulfills(ownClue, it.hex) }
            val map = mutableMapOf<HexPoint, Int>().withDefault { 0 }

            for (i in 0 until possibilities) {
                val specific = Combinatorics.specificPermutation(options, i)
                val clues = deductions.entries.map {
                    it.value.possible[specific[it.key]]
                }
                val potential = remainingHexes.singleOrNull { field ->
                    clues.all { c -> model.fulfills(c, field.hex) }
                }
                if (potential != null) {
                    map[potential.hex] = map.getValue(potential.hex) + 1
                }
            }
            return map
        }

        fun update() {
            selfDeduction.update(model)
            deductions.forEach {
                it.value.update(model)
                println("Deductions for ${it.key}: ${it.value.possible}")
            }
            println("Deductions for ${playerIndex}: ${selfDeduction.possible}")
        }
        private fun options(ownClueKnown: Boolean): IntArray {
            return deductions(ownClueKnown).map { it.value.possible.size }.toIntArray()
        }

        fun bestQuestion(): Question {
            // Find a spot where it's approximately 50% to be a cube or a disc
            val hexes = model.hexGrid.values()
                .filter { model.canPlaceCube(it.hex) }
            val targetPlayer = deductions.entries.maxBy { it.value.possible.size }
            val chosenHex = hexes.minBy { hex ->
                val match = targetPlayer.value.possible.partition { model.fulfills(it, hex.hex) }
                abs(match.first.size - match.second.size)
            }
            return Question(targetPlayer.key, chosenHex.hex)
        }
    }

    fun allClues(advanced: Boolean): List<Clue> {
        val terrains = Terrain.values().flatMap { a ->
            Terrain.values().map { setOf(a, it) }
        }.toSet().toList()
        val list = terrains.map {
            if (it.size == 2) it.first() or it.last()
            else it.first().withinOne()
        }.toMutableList()
        list.addAll(Animal.values().map { it.clue() })
        list.add(Animal.any())
        list.addAll(StructureType.values().map { it.clue() })
        list.addAll(StructureColor.values().filter { it != StructureColor.Black || advanced }.map { it.clue() })
        if (advanced) list.addAll(list.map { it.negative(true) })
        return list
    }

    class Model(
        val placement: CryptidData.MapData,
        val hexGrid: HexGrid<CryptidField>,
        val players: List<Clue>,
        val hint: String,
    ) {
        val advanced = placement.advanced
        private val terrains: Map<Terrain, Set<HexPoint>> = hexGrid.points().groupBy {
            hexGrid.get(it).terrain
        }.mapValues { it.value.toSet() }

        private val animals: Map<Animal, Set<HexPoint>> = hexGrid.points().groupBy {
            hexGrid.get(it).animal
        }.filter { it.key != null }.mapKeys { it.key!! }.mapValues { it.value.toSet() }

        private val structures: Map<Structure, HexPoint> = hexGrid.points()
            .map { hexGrid.get(it).structure to it }
            .filter { it.first != null }
            .associate { it.first!! to it.second }

        var placeCube: Boolean = false
        var currentPlayer: Int = 0

        fun fulfills(clue: Clue, point: HexPoint): Boolean {
            val alternatives = when (val f = clue.feature) {
                is Terrains -> f.terrains.flatMap { terrains.getValue(it) }
                is Animals -> f.animals.flatMap { animals.getValue(it) }
                is StructureColor -> structures.entries.filter { it.key.color == f }.map { it.value }
                is StructureType -> structures.entries.filter { it.key.type == f }.map { it.value }
            }
            val distance = alternatives.minOf { it.distance(point) }
            return (distance <= clue.spaces) == clue.positive
        }

        fun fulfillsRule(playerIndex: Int, point: HexPoint): Boolean = fulfills(players[playerIndex], point)

        fun answer(): HexPoint {
            return hexGrid.points().single { hex ->
                players.indices.all { playerIndex -> fulfillsRule(playerIndex, hex) }
            }
        }

        fun canPlaceCube(parameter: HexPoint): Boolean {
            val pos = hexGrid.getOrNull(parameter) ?: return false
            return pos.cube == null
        }
    }

    enum class Terrain {
        Desert, Water, Swamp, Mountain, Forest;

        infix fun or(other: Terrain): Clue
            = Clue(positive = true, Terrains(setOf(this, other)),0)
        fun withinOne(): Clue = Clue(positive = true, Terrains(setOf(this)), 1)

        companion object {
            fun of(value: String): Terrain = Terrain.values().first { it.name.equals(value, ignoreCase = true) }
            fun ofOrNull(value: String): Terrain? = Terrain.values().firstOrNull { it.name.equals(value, ignoreCase = true) }
        }
    }
    enum class Animal {
        Cougar, Bear;

        fun clue() = Clue(true, Animals(setOf(this)), 2)

        companion object {
            fun any(): Clue = Clue(positive = true, Animals(setOf(Cougar, Bear)), 1)
        }
    }
    sealed interface CryptidFeature

    data class Terrains(val terrains: Set<Terrain>) : CryptidFeature
    data class Animals(val animals: Set<Animal>) : CryptidFeature
    enum class StructureType : CryptidFeature {
        Stone, Shack;
        fun clue() = Clue(true, this, 2)
    }
    enum class StructureColor : CryptidFeature {
        Blue, Green, White, Black;
        fun clue() = Clue(true, this, 3)
        fun shack(): Structure = Structure(StructureType.Shack, this)
        fun stone(): Structure = Structure(StructureType.Stone, this)
    }

    data class Clue(
        val positive: Boolean,
        val feature: CryptidFeature,
        val spaces: Int,
    ) {
        fun negative(negative: Boolean): Clue = Clue(!negative, feature, spaces)
    }

    data class Question(val playerIndex: Int, val point: HexPoint)

    val factory = GamesApi.gameCreator(Model::class)
    val cube = factory.action("cube", HexPoint::class)
    val search = factory.action("search", HexPoint::class)
    val question = factory.action("question", Question::class)
    val game = factory.game("Cryptid") {
        setup {
            players(3..5) // TODO: Add 2 player mode
            init {
                val s = CryptidData.randomMap(true, playerCount)
                println(s.placement)
                println(s.players)
                println(s.hint)
                s
            }
        }
        ai("#AI_Advanced") {
            val deductions = Deductions(game.model, playerIndex)
            action {
                val selfClue = game.model.players[playerIndex]
                deductions.update()
                println("Possible without own clue: " + deductions.possibleHexes(considerOwnClue = false))
                println("Possible with own clue: " + deductions.possibleHexes(considerOwnClue = true))
                val types = game.actions.types().map { it.name }
                if (types.contains(cube.name)) {
                    // Place cube, reveal minimum
                    val hexes = game.model.hexGrid.points()
                        .filter { game.model.canPlaceCube(it) }
                        .filter { !game.model.fulfills(selfClue, it) }
                    val choice = hexes.minBy { hex ->
                        deductions.selfDeduction.possible.count { clue ->
                            game.model.fulfills(clue, hex)
                        }
                    }
                    game.actions.type(cube)!!.createAction(playerIndex, choice)
                } else {
                    // Ask question or search
                    val possibleHexes = deductions.possibleHexes(considerOwnClue = true)
                    if (possibleHexes.size == 1) {
                        game.actions.type(search)!!.createAction(playerIndex, possibleHexes.keys.single())
                    } else {
                        game.actions.type(question)!!.createAction(playerIndex, deductions.bestQuestion())
                    }
                }
            }
        }
        gameFlow {
            repeat(2) {
                for (i in game.players.indices) {
                    game.currentPlayer = i
                    game.placeCube = true
                    step("initial cube placing") {
                        yieldAction(cube) {
                            precondition { playerIndex == i }
                            options { game.hexGrid.points() }
                            requires { game.canPlaceCube(action.parameter) }
                            requires { !game.fulfillsRule(playerIndex, action.parameter) }
                            perform {
                                game.hexGrid.get(action.parameter).cube = i
                                log { "$player places cube at ${action.toStateString()}" }
                            }
                        }
                    }
                }
            }
            game.placeCube = false
            game.currentPlayer = 0
            loop {
                step("action") {
                    yieldAction(search) {
                        precondition { game.currentPlayer == playerIndex }
                        options { game.hexGrid.points() }
                        requires {
                            game.hexGrid.get(action.parameter).cube == null
                        }
                        requires {
                            game.fulfillsRule(playerIndex, action.parameter)
                        }
                        perform {
                            // TODO: Place disc somewhere else if disc already exists here
                            log { "$player searches ${action.toStateString()}" }
                            val pos = game.hexGrid.get(action.parameter)
                            var i = game.currentPlayer
                            var check: Boolean
                            do {
                                check = game.fulfillsRule(i, action.parameter)
                                if (check) {
                                    log { "${player(i)} matches!" }
                                    pos.discs.add(i)
                                } else {
                                    log { "${player(i)} does not match :(" }
                                    pos.cube = i
                                }
                                i = i.next(eliminations)
                            } while (i != game.currentPlayer && check)
                            game.placeCube = !check
                            // Do not advance player because that gets done after you place a cube (if you fail)
                            if (check) {
                                game.players.forEachIndexed { index, clue ->
                                    log {
                                        "${player(index)} had clue $clue"
                                    }
                                }
                                eliminations.singleWinner(playerIndex)
                            }
                        }
                    }
                    yieldAction(question) {
                        precondition { game.currentPlayer == playerIndex }
                        requires { game.currentPlayer != action.parameter.playerIndex }
                        requires { game.hexGrid.get(action.parameter.point).cube == null }
                        choose {
                            options({ game.players.indices - game.currentPlayer }) { questionPlayer ->
                                options({ game.hexGrid.points() }) { point ->
                                    parameter(Question(questionPlayer, point))
                                }
                            }
                        }
                        perform {
                            val pos = game.hexGrid.get(action.parameter.point)
                            if (game.fulfillsRule(action.parameter.playerIndex, action.parameter.point)) {
                                pos.discs.add(action.parameter.playerIndex)
                                game.currentPlayer = game.currentPlayer.next(eliminations)
                                game.placeCube = false
                            } else {
                                pos.cube = action.parameter.playerIndex
                                game.placeCube = true
                            }
                            log { "$player questions ${player(action.playerIndex)} about ${action.point.toStateString()} resulting in: ${!game.placeCube}" }
                        }
                    }
                }
                if (game.placeCube) {
                    step("place cube") {
                        yieldAction(cube) {
                            precondition { game.currentPlayer == playerIndex }
                            options { game.hexGrid.points() }
                            requires { game.canPlaceCube(action.parameter) }
                            requires { !game.fulfillsRule(playerIndex, action.parameter) }
                            perform {
                                game.hexGrid.get(action.parameter).cube = game.currentPlayer
                                game.currentPlayer = game.currentPlayer.next(eliminations)
                                game.placeCube = false
                                log { "$player places cube at ${action.toStateString()}" }
                            }
                        }
                    }
                }
            }
        }
        gameFlowRules {
            beforeReturnRule("view") {
                view("hexGrid") {
                    game.hexGrid.hexView { it }
                }
                view("grid") {
                    game.placement
                }
                view("cubes") { game.hexGrid.values().filter { it.value.cube != null }.associate { it.hex to it.value.cube } }
                view("discs") { game.hexGrid.values().filter { it.value.discs.isNotEmpty() }.associate { it.hex to it.value.discs } }
                view("hint") { game.hint }
                view("currentPlayer") { game.currentPlayer }
                view("placeCube") { game.placeCube }
                view("clue") { game.players.getOrNull(viewer ?: -1) }
            }
        }
    }

}