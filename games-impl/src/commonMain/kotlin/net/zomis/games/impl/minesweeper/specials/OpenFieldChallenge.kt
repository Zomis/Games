package net.zomis.games.impl.minesweeper.specials

import net.zomis.games.PlayerEliminationsWrite
import net.zomis.games.WinResult
import net.zomis.games.api.GamesApi
import net.zomis.games.components.Point
import net.zomis.games.components.grids.GridPoint
import net.zomis.games.components.grids.connectedAreas
import net.zomis.games.dsl.ReplayStateI
import net.zomis.games.impl.minesweeper.*
import net.zomis.games.impl.minesweeper.ais.point
import net.zomis.minesweeper.analyze.AnalyzeResult

enum class OpenFieldChallengeDifficulty {
    /**
     * One open field per board, Only need easy mines, A lot of extra life, Start with 10 life,
     */
    EASY,

    /**
     * Three or so open fields per board, Only needs to take easy and medium mines (AI Challenger style),
     * Quite a lot of extra life
     */
    MEDIUM,

    /**
     * No extra life. No information about mines left, All mines must be taken
     */
    HARD,

    /**
     * No extra life. No information about mines left, Must click button when you think all mines are found,
     * All mines must be taken, no mines can be missed
     */
    EXTREME,
}
object OpenFieldChallenge {
    private const val CERTAIN_MINE_THRESHOLD = 0.9999

    data class OFCScore(
        var minesFound: Int = 0,
        var points: Int = 0,
        var timeStarted: Long = 0,
        var clearedBoards: Int = 0,
        var mistakesAllowed: Int = 0,
        val mistakesMade: MutableSet<Flags.Field> = mutableSetOf(),
        val correctAnswers: MutableSet<Flags.Field> = mutableSetOf(),
        var minesRequired: Int? = null,
    )
    class Model(
        val difficulty: OpenFieldChallengeDifficulty,
        val model: Flags.Model,
        private val minesCount: Int,
        val score: OFCScore = OFCScore(),
    ) {
        private var fieldsOpened = 0
        private var analysis: AnalyzeResult<Flags.Field> = MfeAnalyze.analyze(model)
        init {
            score.mistakesAllowed = when (difficulty) {
                OpenFieldChallengeDifficulty.EASY -> 10
                OpenFieldChallengeDifficulty.MEDIUM -> 3
                else -> 0
            }
        }

        fun singleGuess(eliminations: PlayerEliminationsWrite, field: Flags.Field, replayable: ReplayStateI) {
            check(difficulty != OpenFieldChallengeDifficulty.EXTREME)
            if (isCertain(field)) {
                Weapons.reveal(model, model.currentPlayer, field, expand = false)
                score.minesFound++
                score.points += 3
                score.minesRequired = score.minesRequired?.let { it - 1 }
            } else {
                println("Field ${field.point} had probability ${analysis.getGroupFor(field)?.probability}")
                analysis.rules.forEach {
                    println(it)
                }
                score.mistakesMade.add(field)
                if (score.mistakesAllowed <= 0) {
                    score.correctAnswers += certainFields()
                    eliminations.eliminateRemaining(WinResult.WIN) // TODO: Target score for each challenge?
                } else score.mistakesAllowed--
            }
            val remaining = certainFields().filter { !it.clicked }
            if (remaining.isEmpty()) {
                openField(replayable)
            }
        }

        private fun certainFields() = analysis.groups
            .filter { it.probability > CERTAIN_MINE_THRESHOLD }
            .flatMap { it.fields }

        fun multiGuess(eliminations: PlayerEliminationsWrite, list: List<Flags.Field>) {
            check(difficulty == OpenFieldChallengeDifficulty.EXTREME)
            val correct = certainFields()
            val incorrectlyMarkedMines = list.minus(correct.toSet())
            val missedMines = correct.minus(list.toSet())
            val correctlyMarkedMines = list.union(correct)
            score.points -= incorrectlyMarkedMines.size * 5
            correctlyMarkedMines.forEach { Weapons.reveal(model, model.currentPlayer, it, expand = false) }
            score.points += correctlyMarkedMines.size * 3
            if (missedMines.isNotEmpty() || incorrectlyMarkedMines.isNotEmpty()) {
                score.mistakesMade += incorrectlyMarkedMines
                score.correctAnswers += missedMines
                eliminations.eliminateRemaining(WinResult.WIN)
            }
        }

        fun isCertain(field: Flags.Field) = analysis.getGroupFor(field)!!.probability > CERTAIN_MINE_THRESHOLD

        fun openField(replayable: ReplayStateI) {
            checkNewBoard(replayable)
            val field = chooseNextOpenField(replayable)
            if (field == null) {
                newBoard(replayable)
                openField(replayable)
                return
            }
            fieldsOpened++
            Weapons.reveal(model, model.currentPlayer, field, expand = true)
            updateAnalysis(replayable)
        }

        private fun checkNewBoard(replayable: ReplayStateI) {
            when (difficulty) {
                OpenFieldChallengeDifficulty.EASY -> if (fieldsOpened >= 1) {
                    newBoard(replayable)
                }
                OpenFieldChallengeDifficulty.MEDIUM -> if (fieldsOpened >= 3) {
                    newBoard(replayable)
                }
                else -> {}
            }
        }

        private fun chooseNextOpenField(replayable: ReplayStateI): Flags.Field? {
            val allOpenFields = model.grid.connectedAreas(
                neighbors = {
                    it.value.inverseNeighbors.map { n -> model.grid.point(n.point) }
                }, groupFunction = {
                    if (it.value.isMine()) -1 else it.value.value
                }, originFilter = {
                    it.value.value == 0 && !it.value.isMine() && !it.value.clicked
                }
            )
            if (allOpenFields.isEmpty()) return null
            val reveal = if (difficulty == OpenFieldChallengeDifficulty.EASY) {
                allOpenFields.maxBy { it.points.size }
            } else {
                replayable.randomFromList("openField$fieldsOpened", allOpenFields, 1) { area ->
                    area.points.minWith(compareBy<GridPoint<Flags.Field>> { it.y }.thenBy { it.x }).point.toStateString()
                }.first()
            }
            return reveal.points.random().value
        }

        private fun updateAnalysis(replayable: ReplayStateI) {
            analysis = MfeAnalyze.analyze(model)
            when (difficulty) {
                OpenFieldChallengeDifficulty.EASY -> TODO()
                OpenFieldChallengeDifficulty.MEDIUM -> TODO()
                OpenFieldChallengeDifficulty.HARD -> {
                    if (certainFields().isEmpty()) openField(replayable)
                }
                OpenFieldChallengeDifficulty.EXTREME -> {}
            }
        }

        private fun newBoard(replayable: ReplayStateI) {
            score.mistakesMade.clear()
            score.points += pointsBonusClearedMap
            score.clearedBoards++
            fieldsOpened = 0
            Setup.generate(model, replayable, minesCount)
            openField(replayable)
        }
        private val pointsBonusClearedMap = when (difficulty) {
            OpenFieldChallengeDifficulty.EASY -> 10
            OpenFieldChallengeDifficulty.MEDIUM -> 10
            OpenFieldChallengeDifficulty.HARD -> 15
            OpenFieldChallengeDifficulty.EXTREME -> 25
        }
    }


    class ViewModelOFC(model: Model, viewer: Int) {
        val difficulty = model.difficulty
        val scores = model.score.copy(mistakesMade = mutableSetOf(), correctAnswers = mutableSetOf())
        val mistakesMade = model.score.mistakesMade.map { it.point }
        val correctAnswers = model.score.correctAnswers.map { it.point }
        val model = net.zomis.games.impl.minesweeper.ViewModel(model.model, viewer)
    }

    fun ofcGoal(eliminations: PlayerEliminationsWrite, game: Model) {
        if (game.score.mistakesAllowed < 0) eliminations.eliminateRemaining(WinResult.WIN)
    }
    data class FieldGuesses(val fields: List<Flags.Field>)

    val factory = GamesApi.gameCreator(Model::class)
    val viewModel = factory.viewModel(::ViewModelOFC)
    val singleGuess = factory.action("click", Flags.Field::class).serializer {
        it.point.toStateString()
    }
    val makeGuesses = factory.action("confirm", FieldGuesses::class).serialization({
        it.fields.joinToString(separator = "+", transform = Flags.Field::toStateString)
    }, { s ->
        if (s.isEmpty()) return@serialization FieldGuesses(emptyList())
        FieldGuesses(s.split("+").map { Point.fromString(it) }.map { game.model.fieldAt(it) })
    })
    val game = factory.game("MFE-OFC") {
        val width = config("width") { 16 }
        val height = config("height") { 16 }
        val neighbors = config("neighbors") { NeighborStyle.NORMAL }
        val mineCount = config("mines") { 51 }
        val difficulty = config("difficulty") { OpenFieldChallengeDifficulty.HARD }
        setup {
            init {
                Model(config(difficulty), Flags.Model(playerCount, Point(config(width), config(height))), config(mineCount))
            }
            playersFixed(1)
        }
        gameFlow {
            val model = game.model
            Neighbors.configure(model, config(neighbors))
            Setup.generate(model, replayable, config(mineCount))
            game.openField(replayable)

            loop {
                step("gameplay") {
                    println("Gameplay ${game.difficulty}")
                    if (game.difficulty == OpenFieldChallengeDifficulty.EXTREME) {
                        yieldAction(makeGuesses) {
                            choose {
                                recursive(emptyList<Flags.Field>()) {
                                    intermediateParameter { true }
                                    parameter {
                                        FieldGuesses(chosen)
                                    }
                                    options({ model.grid.points() }) {
                                        recursion(model.grid.point(it)) { old, field ->
                                            if (old.contains(field.value)) {
                                                old.minus(field.value)
                                            } else {
                                                old.plus(field.value)
                                            }
                                        }
                                    }
                                }
                            }
                            perform {
                                game.multiGuess(eliminations, action.parameter.fields)
                                if (!eliminations.isGameOver()) {
                                    game.openField(replayable)
                                }
                            }
                        }
                    } else {
                        yieldAction(singleGuess) {
                            requires {
                                !action.parameter.clicked
                            }
                            perform {
                                game.singleGuess(eliminations, action.parameter, replayable)
                            }
                            choose {
                                options({ model.grid.points() }) { pos ->
                                    parameter(model.grid.point(pos).value)
                                }
                            }
                        }
                    }
                }
            }
        }
        gameFlowRules {
            beforeReturnRule("view") {
                viewModel(viewModel)
            }
        }
    }
}
