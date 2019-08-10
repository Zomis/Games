package net.zomis.games.server2

import kotlinx.coroutines.*
import net.zomis.aiscores.*
import net.zomis.fight.ext.Fight
import net.zomis.games.ais.AlphaBeta
import net.zomis.games.ais.Best
import net.zomis.scorers.*
import net.zomis.scorers.FieldScore
import org.jetbrains.kotlin.utils.addToStdlib.measureTimeMillisWithResult
import java.util.Scanner
import java.util.Random
import kotlin.streams.toList

fun loadMap(text: String): TTT3D {
    val game = TTT3D()
    text.replace(" ", "").split("/").map { it.split('|') }
            .forEachIndexed { y, row ->
                row.forEachIndexed { x, column ->
                    column.chars().toList().forEachIndexed { z, ch ->
                        if (ch.toChar() == 'X') {
                            game.pieces[y][x][z].piece = TTT3DPiece.X
                        } else {
                            game.pieces[y][x][z].piece = TTT3DPiece.O
                        }
                    }
                }
            }
    return game
}

enum class TTT3DPiece(val playerIndex: Int) {
    X(0), O(1);

    fun opponent(): TTT3DPiece = if (this == TTT3DPiece.X) TTT3DPiece.O else TTT3DPiece.X
}

data class TTT3DPoint(val x: Int, val y: Int, val z: Int, var piece: TTT3DPiece?)

data class TTT3DWinCondition(val pieces: List<TTT3DPoint>) {

    fun contains(point: TTT3DPoint?): Boolean {
        return pieces.contains(point)
    }

    fun canWin(player: TTT3DPiece): Boolean {
        return pieces.none { it.piece != null && it.piece != player }
    }

    fun emptySpaces(): Int {
        return pieces.count { it.piece == null }
    }

}

suspend fun <A, B> List<A>.pmap(f: suspend (A) -> B): List<B> = coroutineScope {
    map { async(Dispatchers.Default) { f(it) } }.map { it.await() }
}

private val RANGE: IntRange = (0 until 4)
class TTT3D {

    val pieces: Array<Array<Array<TTT3DPoint>>> = RANGE.map { y ->
        RANGE.map { x ->
            RANGE.map { z -> TTT3DPoint(x, y, z, null) }.toTypedArray()
        }.toTypedArray()
    }.toTypedArray()
    var currentPlayer = TTT3DPiece.X

    val winConditions: List<TTT3DWinCondition>

    init {
        val conditions = mutableListOf<TTT3DWinCondition>()

        RANGE.forEach { a ->
            RANGE.forEach { b ->
                conditions.add(findConditions(a to 0, b to 0, 0 to 1))
                conditions.add(findConditions(a to 0, 0 to 1, b to 0))
                conditions.add(findConditions(0 to 1, a to 0, b to 0))
            }
            // Diagonals per plane
            conditions.add(findConditions(0 to 1, 0 to 1, a to 0))
            conditions.add(findConditions(RANGE.last to -1, 0 to 1, a to 0))

            // Diagonals per row
            conditions.add(findConditions(a to 0, 0 to 1, 0 to 1))
            conditions.add(findConditions(a to 0, RANGE.last to -1, 0 to 1))

            // Diagonals per column
            conditions.add(findConditions(0 to 1, a to 0, 0 to 1))
            conditions.add(findConditions(RANGE.last to -1, a to 0, 0 to 1))
        }

        // Diagonals from bottom corner to top opposite corner
        conditions.add(findConditions(0 to 1, 0 to 1, 0 to 1))
        conditions.add(findConditions(RANGE.last to -1, 0 to 1, 0 to 1))
        conditions.add(findConditions(RANGE.last to -1, RANGE.last to -1, 0 to 1))
        conditions.add(findConditions(0 to 1, RANGE.last to -1, 0 to 1))

        winConditions = conditions
    }

    private fun findConditions(y: Pair<Int, Int>, x: Pair<Int, Int>, z: Pair<Int, Int>): TTT3DWinCondition {
        var yy = y.first
        var xx = x.first
        var zz = z.first
        val max = RANGE.last
        val result = mutableListOf<TTT3DPoint>()
        while (xx <= max && yy <= max && zz <= max) {
            val tile = pieces[yy][xx][zz]
            result.add(tile)

            xx += x.second
            yy += y.second
            zz += z.second
        }
        assert(result.size == 4)
        return TTT3DWinCondition(result)
    }

    fun playAt(y: Int, x: Int): Boolean {
//        println("$currentPlayer plays at $y $x")
        if (!RANGE.contains(x) || !RANGE.contains(y)) {
            return false
        }
        val spot = this.pieces[y][x]
        val point = spot.firstOrNull { it.piece == null }
        point?.piece = currentPlayer
        if (point != null) {
            currentPlayer = currentPlayer.opponent()
            return true
        } else {
            return false
        }
    }

    fun findWinner(): TTT3DPiece? {
        return winConditions.find { winCond ->
            val pieces = winCond.pieces.map { list -> list.piece }
            val notNull = pieces.filterNotNull()
            return@find notNull.size == 4 && pieces.all { it == pieces.first() }
        }?.pieces?.firstOrNull()?.piece
    }

    fun canPlayAt(field: TTT3DPoint): Boolean {
        return field.piece == null &&
            this.pieces[field.y][field.x].firstOrNull { it.piece == null } == field
    }

    fun isDraw(): Boolean {
        return allFields().none { it.piece == null }
    }

    fun allFields(): Sequence<TTT3DPoint> {
        return this.pieces.flatten().flatMap { it.toList() }.asSequence()
    }

    fun copy(): TTT3D {
        val other = TTT3D()
        this.allFields().forEach { other.pieces[it.y][it.x][it.z].piece = it.piece }
        other.currentPlayer = this.currentPlayer
        return other
    }

    override fun toString(): String {
        return "Player $currentPlayer Fields ${allFields().toList()}"
    }

}

class TTT3DScorer(private val game: TTT3D): ScoreStrategy<TTT3DPiece, TTT3DPoint> {
    override fun canScoreField(parameters: ScoreParameters<TTT3DPiece>?, field: TTT3DPoint?): Boolean {
        return canScore(parameters!!.parameters, field!!)
    }

    fun canScore(player: TTT3DPiece, field: TTT3DPoint): Boolean {
        return game.currentPlayer == player && game.canPlayAt(field)
    }

    fun canScore(player: ScoreParams<TTT3DPiece>, field: TTT3DPoint): Boolean {
        return game.currentPlayer == player.param && game.canPlayAt(field)
    }

    fun fieldsToScoreInGame(state: TTT3D): Sequence<TTT3DPoint> {
        return state.pieces.flatten().flatMap { it.toList() }.filter {
            state.canPlayAt(it)
        }.asSequence()
    }

    override fun getFieldsToScore(parameters: TTT3DPiece?): MutableCollection<TTT3DPoint> {
        if (game.currentPlayer != parameters) {
            return mutableListOf()
        }
        return fieldsToScoreInGame(game).toMutableList()
    }

}

class TTT3DIO(private val game: TTT3D) {
    val scorerStrategy = TTT3DScorer(game)
    val scorers = Scorers<TTT3DPiece, TTT3DPoint>(scorerStrategy::getFieldsToScore, scorerStrategy::canScore)
    val scorerWant = NamedScorer<TTT3DPiece, TTT3DPoint>("want") { piece, point ->
        game.winConditions.filter { it.contains(point) }.filter { it.canWin(piece.param) }.map {
            when (it.emptySpaces()) {
                1 -> 100.0
                2 -> 1.0
                3 -> 0.5
                else -> 0.01
            }
        }.sum()
    }
    val scorerSabotage = NamedScorer<TTT3DPiece, TTT3DPoint>("sabotage") { piece, point ->
        game.winConditions.filter { it.contains(point) }.filter { it.canWin(piece.param.opponent()) }.map {
            when (it.emptySpaces()) {
                1 -> 50.0
                2 -> 1.0
                3 -> 0.5
                else -> 0.0
            }
        }.sum()
    }
    val alerts = NamedScorer<TTT3DPiece, TTT3DPoint>("alerts") { piece, point ->
        val block = scorerSabotage.scoring(piece, point) > 30
        val doNotPlace = nextLevelReveal.scoring(piece, point) < -30
        val i = block.let { if (it) 30 else 0 } + doNotPlace.let { if (it) -40 else 0 }
        return@NamedScorer i.toDouble()
    }
    // Trap scorer: Extra points for creating traps -- if you force opponent to play somewhere that will give you the win
    // Field importance scorer: If you can create a win condition that will cause a trap (field is important for you)
    // Trigger Trap scorer: Extra points if you only need 1 piece to win on BOTH of the levels above point.
    val nextLevelReveal = NamedScorer<TTT3DPiece, TTT3DPoint>("nextReveal") { piece, point ->
        if (point.z == RANGE.endInclusive) {
            return@NamedScorer 0.0
        }
        game.winConditions.filter { it.contains(game.pieces[point.y][point.x][point.z + 1]) }
                .filter { !it.contains(point) }
                .filter { it.canWin(piece.param.opponent()) }.map { it.emptySpaces() }.map {
                    when (it) {
                        1 -> -50.0
                        2 -> -1.0
                        else -> 0.0
                    }
                }.sum()
    }


    fun print() {
        print { it.piece?.toString() ?: " " }
        val xwins = game.winConditions.filter { it.canWin(TTT3DPiece.X) }.groupBy { it.emptySpaces() }.mapValues { it.value.size }
        val owins = game.winConditions.filter { it.canWin(TTT3DPiece.O) }.groupBy { it.emptySpaces() }.mapValues { it.value.size }
        println("Winnables: X $xwins (${xwins.values.sum()}). O $owins (${owins.values.sum()})")
    }

    fun print(function: (TTT3DPoint) -> String) {
        val rowSeperator = "-".repeat((RANGE.last + 1) * (RANGE.last + 4) + 1)
        println(rowSeperator)
        game.pieces.forEach { y ->
            print("|")
            y.forEach { x ->
                print(" ")
                x.forEach { z ->
                    print(function(z))
                }
                print(" |")
            }
            println()
            println(rowSeperator)
        }
        println()
    }

    val factory = scorers.config()
            .withScorer(scorerWant)
            .withScorer(scorerSabotage)
            .withScorer(nextLevelReveal)

    fun printScores(factory: ScorersConfig<TTT3DPiece, TTT3DPoint>) {
        val scoreDetailsToString: (Pair<String, Double>) -> String = { score ->
            val format = "%.4f"
            "${score.first}: ${format.format(score.second)}"
        }

        val scores = factory.producer(game.currentPlayer).score()
        val maxLength: Int = scores.fieldScores.values.flatMap {
            it.scores.map { score -> scoreDetailsToString(score.key to score.value).length + 2 }
        }.max() ?: 0

        val rowSeperator = "-".repeat((RANGE.last + 1) * (RANGE.last + 4) + 1)
        println(rowSeperator)
        game.pieces.forEachIndexed { y, yArray ->
            val range = -1 until factory.scorers.size
            for (i in range) {
                print("|")
                val strFunction: (FieldScore<TTT3DPoint>) -> String
                if (i == -1) {
                    strFunction = { score -> "Total ${score.score}" }
                } else {
                    strFunction = { score -> scoreDetailsToString(score.scores.toList()[i]) }
                }
                yArray.forEachIndexed { x, xArray ->
                    val fieldScore = scores.fieldScores.values.find { it.field.x == x && it.field.y == y }
                    print(" ")
                    val string = fieldScore?.let(strFunction) ?: ""
                    print(string.padEnd(maxLength))
                    print(" |")
                }
                println()
            }
            println(rowSeperator)
        }
        println()
    }

    fun playVsAI() {
        val scanner = Scanner(System.`in`)

        while (game.findWinner() == null && scorers.fieldsToScore(game.currentPlayer).toList().isNotEmpty()) {
            if (game.currentPlayer == TTT3DPiece.X) {
//                requestInput(scanner)
//                this.aiPlay()
                alphaBetaPlay()
            } else {
                this.alphaBetaPlay()
//                this.aiPlay()
            }
            this.print()
//            Thread.sleep(2000)
        }
        print()
        println(game.findWinner())
    }

    fun alphaBetaPlay() {
        val move = alphaBeta()
        game.playAt(move.y, move.x)
    }

    fun canWin(state: TTT3D, field: TTT3DPoint): Boolean {
        return state.winConditions.any { it.contains(field) && it.canWin(state.currentPlayer) && it.emptySpaces() == 1 }
    }

    fun mustBlock(state: TTT3D, field: TTT3DPoint): Boolean {
        val opponent = state.currentPlayer.opponent()
        return state.winConditions.any { it.contains(field) && it.canWin(opponent) && it.emptySpaces() == 1 }
    }

    fun alphaBeta(): TTT3DPoint {
        val actions: (TTT3D) -> List<TTT3DPoint> = lambda@{ state ->
            val allFields = scorerStrategy.fieldsToScoreInGame(state).toList()
            val canWin = allFields.filter { canWin(state, it) }
            if (canWin.isNotEmpty()) {
                return@lambda canWin
            }

            val mustBlock = allFields.filter { mustBlock(state, it) }
            if (mustBlock.isNotEmpty()) {
                return@lambda mustBlock
            }
            allFields
        }
        val branching: (TTT3D, TTT3DPoint) -> TTT3D = { state, position ->
            val copy = state.copy()
            copy.playAt(position.y, position.x)
            copy
        }
        val heuristic: (TTT3D) -> Double = { state ->
            val opp = game.currentPlayer // 'I' just played so it's opponent's turn
            val me = opp.opponent()
            var result = 0.0
            if (state.findWinner() != null) {
                result = if (state.findWinner() == me) 100.0 else -100.0
            } else {
                val myWins = state.winConditions.filter { it.canWin(me) }.groupBy { it.emptySpaces() }.mapValues { it.value.size }
                val opWins = state.winConditions.filter { it.canWin(opp) }.groupBy { it.emptySpaces() }.mapValues { it.value.size }
                val positive = (myWins[1]?:0) * 4 + (myWins[2]?:0) * 2 + (myWins[3]?:0) * 0.1
                val negative = (opWins[1]?:0) * 4 + (opWins[2]?:0) * 2 + (opWins[3]?:0) * 0.1
                result = positive - negative
            }
            -result.toDouble()
        }
        val ab = AlphaBeta<TTT3D, TTT3DPoint>(actions, branching, { it.findWinner() != null || it.isDraw()}, heuristic)
        val timeAndOptions = measureTimeMillisWithResult {
            runBlocking {
                actions(game).pmap { action ->
                    val newState = branching(game, action)
                    action to ab.score(newState, 5)
                }.toList()
            }
        }

        println("AlphaBeta analyzed in ${timeAndOptions.first} ms")
        val options = timeAndOptions.second

        val best = Best<Pair<TTT3DPoint, Double>>(true)
        options.forEach {
            println(it)
            best.next(it, it.second)
        }
        val move = best.random() //options.maxBy { it.second }!!

//        val move = ab.alphaBeta(game, 5)
        println("AI Decided on $move")
        return move.first
    }

    fun aiPlay() {
        this.printScores(this.factory)
        val random = Random()

        val ai = factory.producer(game.currentPlayer)
        val scores = ai.score()


        val best = scores.best()
        if (best.isEmpty()) {
            return
        }
        val randomBestField = best[random.nextInt(best.size)]
        game.playAt(randomBestField.y, randomBestField.x)
    }

    fun play() {
        this.print { point -> game.winConditions.count { it.pieces.contains(point) }.toString() }

        val scanner = Scanner(System.`in`)
        while (game.findWinner() == null) {
            this.requestInput(scanner)
        }
        println("Winner is ${game.findWinner()}")
        this.print()
    }

    private fun requestInput(scanner: Scanner) {
        val alertConfig = scorers.config().withScorer(this.alerts)
        this.printScores(alertConfig)
        println()
        this.print()
        println("Where do you want to move? Current Player ${game.currentPlayer}")
        val x = scanner.nextInt()
        val y = scanner.nextInt()

        if (!game.playAt(y, x)) {
            println("Not a valid position to play at")
        }
    }
/*
    data class MoveData(floorsCount)
    data class GameData(ais, winner)
    fun fight() {
        val scorerAI = null0
        val alphaBeta = null
        val alphaBeta6 = null
        val alphaBeta4 = null
        Fights()
            .fight { players ->
                val game = TTT3D()
                while (!game.isGameOver()) {
                    val move = players[game.currentPlayer.playerIndex].play(game)
                    game.playAt(move)
                    this.save(move)
                    this.save(game)
                }
                this.finish(game)
            }
            .vs(scorerAI, alphaBeta)
            .index("player")
//            .dataFinish(moveCount)

            .fight(100)
    }
*/
//    val a = Collectors.
}

fun main(args: Array<String>) {
    val game = loadMap("XXO  |      | XO   | OX   /      | OXO  | OOOX |      / XX   | XXO  |      | OO   / OX   |      |      | XX   ")
    TTT3DIO(game).playVsAI()

}
/*
fun game() {
    games.registerGame("TTT3D")
        .websocket()
        .players(2)
        .lobby()
        .setup { TTT3D() }
        .action("play") { json ->

        }
        .fullState { playerInGame ->
            return mapOf("tiles" to this.pieces)
        }
        .
}
*/
