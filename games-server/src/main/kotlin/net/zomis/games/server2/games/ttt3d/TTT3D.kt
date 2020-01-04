package net.zomis.games.server2.games.ttt3d

import kotlinx.coroutines.*
import net.zomis.Best
import net.zomis.best
import net.zomis.bestBy
import net.zomis.common.pmap
import net.zomis.fight.ext.WinResult
import net.zomis.fights.Fights
import net.zomis.games.ais.AlphaBeta
import net.zomis.games.impl.*
import net.zomis.scorers.*
import net.zomis.scorers.FieldScore
import java.util.Scanner
import kotlin.streams.toList

private val RANGE: IntRange = (0 until 4)
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

class TTT3DScorer {

    fun canScore(state: ScoreParams<TTT3D>, field: TTT3DPoint): Boolean {
        return state.param.canPlayAt(field)
    }

    fun fieldsToScoreInGame(state: TTT3D): Sequence<TTT3DPoint> {
        return state.pieces.flatten().flatMap { it.toList() }.filter {
            state.canPlayAt(it)
        }.asSequence()
    }

    fun getFieldsToScore(state: TTT3D): MutableCollection<TTT3DPoint> {
        return fieldsToScoreInGame(state).toMutableList()
    }

}

class TTT3DIO {
    val scorerStrategy = TTT3DScorer()
    val scorers = Scorers(scorerStrategy::getFieldsToScore, scorerStrategy::canScore)
    val scorerWant = NamedScorer<TTT3D, TTT3DPoint>("want") { params, point ->
        params.param.winConditions.filter { it.contains(point) }.filter { it.canWin(params.param.currentPlayer) }.map {
            when (it.emptySpaces()) {
                1 -> 100.0
                2 -> 1.0
                3 -> 0.5
                else -> 0.01
            }
        }.sum()
    }
    val scorerSabotage = NamedScorer<TTT3D, TTT3DPoint>("sabotage") { params, point ->
        params.param.winConditions.filter { it.contains(point) }.filter { it.canWin(params.param.currentPlayer.opponent()) }.map {
            when (it.emptySpaces()) {
                1 -> 50.0
                2 -> 1.0
                3 -> 0.5
                else -> 0.0
            }
        }.sum()
    }
    val alerts = NamedScorer<TTT3D, TTT3DPoint>("alerts") { params, point ->
        val block = scorerSabotage.scoring(params, point) > 30
        val doNotPlace = nextLevelReveal.scoring(params, point) < -30
        val i = block.let { if (it) 30 else 0 } + doNotPlace.let { if (it) -40 else 0 }
        return@NamedScorer i.toDouble()
    }
    // Trap scorer: Extra points for creating traps -- if you force opponent to play somewhere that will give you the win
    // Field importance scorer: If you can create a win condition that will cause a trap (field is important for you)
    // Trigger Trap scorer: Extra points if you only need 1 piece to win on BOTH of the levels above point.
    val nextLevelReveal = NamedScorer<TTT3D, TTT3DPoint>("nextReveal") { params, point ->
        if (point.z == RANGE.endInclusive) {
            return@NamedScorer 0.0
        }
        params.param.winConditions.filter { it.contains(params.param.pieces[point.y][point.x][point.z + 1]) }
                .filter { !it.contains(point) }
                .filter { it.canWin(params.param.currentPlayer.opponent()) }.map { it.emptySpaces() }.map {
                    when (it) {
                        1 -> -50.0
                        2 -> -1.0
                        else -> 0.0
                    }
                }.sum()
    }


    fun print(game: TTT3D) {
        print(game) { it.piece?.toString() ?: " " }
        val xwins = game.winConditions.filter { it.canWin(TTT3DPiece.X) }.groupBy { it.emptySpaces() }.mapValues { it.value.size }
        val owins = game.winConditions.filter { it.canWin(TTT3DPiece.O) }.groupBy { it.emptySpaces() }.mapValues { it.value.size }
        println("Winnables: X $xwins (${xwins.values.sum()}). O $owins (${owins.values.sum()})")
    }

    fun print(game: TTT3D, function: (TTT3DPoint) -> String) {
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

    fun printScores(game: TTT3D, factory: ScorersConfig<TTT3D, TTT3DPoint>) {
        val scoreDetailsToString: (Pair<String, Double>) -> String = { score ->
            val format = "%.4f"
            "${score.first}: ${format.format(score.second)}"
        }

        val scores = factory.producer(game).score()
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

    fun playVsAI(game: TTT3D, ai: NamedAI, human: TTT3DPiece) {
        val scanner = Scanner(System.`in`)

        while (!game.isGameOver()) {
            game.standardize()

            if (game.currentPlayer == human) {
                makeMove(game, requestInput(game, scanner))
            } else {
                makeMove(game, ai.ai(game))
            }
            this.print(game)
//            Thread.sleep(2000)
        }
        print(game)
        println(game.findWinner())
    }

    private fun makeMove(game: TTT3D, move: Pair<Int, Int>) {
        val x = move.first
        val y = move.second
        println("${game.currentPlayer} moves at $x, $y")
        if (!game.playAt(y, x)) {
            println("Not a valid position to play at")
        }
    }

    fun alphaBetaPlay(game: TTT3D, depth: Int): Pair<Int, Int> {
        val move = alphaBeta(game, depth)
        return move.x to move.y
    }

    fun canWin(state: TTT3D, field: TTT3DPoint): Boolean {
        return state.winConditions.any { it.contains(field) && it.canWin(state.currentPlayer) && it.emptySpaces() == 1 }
    }

    fun mustBlock(state: TTT3D, field: TTT3DPoint): Boolean {
        val opponent = state.currentPlayer.opponent()
        return state.winConditions.any { it.contains(field) && it.canWin(opponent) && it.emptySpaces() == 1 }
    }

    fun alphaBeta(game: TTT3D, depth: Int): TTT3DPoint {
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

        val options = runBlocking {
            actions(game).pmap { action ->
                val newState = branching(game, action)
                action to ab.score(newState, depth)
            }.toList()
        }

//        println("Scores for AlphaBeta $depth")
//        printScores(game, scorers.config().withScorer(scorerAlphaBeta(options)))
        val move = options.bestBy { it.second }.random() //options.maxBy { it.second }!!

        return move.first
    }

    fun scorerAlphaBeta(options: List<Pair<TTT3DPoint, Double>>) = NamedScorer<TTT3D, TTT3DPoint>("alphaBeta") { params, point ->
        val foundValue = options.find { it.first == point }?.second
        if (foundValue == null) {
            println("WARNING: No such point found: $point in $options")
            return@NamedScorer -420.0
        }
        return@NamedScorer foundValue
    }

    fun aiPlay(game: TTT3D): Pair<Int, Int> {
        this.printScores(game, this.factory)

        val ai = factory.producer(game)
        val scores = ai.score()
        val best = scores.best()
        if (best.isEmpty()) {
            throw IllegalStateException("No place to move")
        }
        val randomBestField = best.random()
        return randomBestField.x to randomBestField.y
    }

    private fun requestInput(game: TTT3D, scanner: Scanner): Pair<Int, Int> {
        val alertConfig = scorers.config().withScorer(this.alerts)
        this.printScores(game, alertConfig)
        println()
        this.print(game)
        println("Where do you want to move? Current Player ${game.currentPlayer}")
        val x = scanner.nextInt()
        val y = scanner.nextInt()
        return x to y
    }

    data class MoveData(val floorsCount: List<Int>)
    data class GameData(val ais: List<Any>, val winner: Any)
    data class NamedAI(val name: String, val ai: TTT3DAI) {
        override fun toString(): String {
            return name
        }
    }

    fun fight() {
        val fightResult = Fights()
            .between(NamedAI("Scorer", scorerAI(factory)), alphaBetaAI(3), alphaBetaAI(4),
                alphaBetaAI(5), alphaBetaAI(6))
            .fight { fight ->
                val game = TTT3D()
                while (!game.isGameOver()) {
                    val move = fight.players[game.currentPlayer.playerIndex].ai.invoke(game)
                    if (!game.playAt(move.second, move.first)) {
                        print(game)
                        throw IllegalArgumentException("Illegal move: $move")
                    }
                }
                println("Players ${fight.players} result: ${game.findWinner()}")
                fight.gameResult(fight.players[0], WinResult.result(game.isDraw(), game.findWinner() == TTT3DPiece.X))
                fight.gameResult(fight.players[1], WinResult.result(game.isDraw(), game.findWinner() == TTT3DPiece.O))
            }
            .fightEvenly(5)

        fightResult.print()
//            .index("player")
//            .dataFinish(moveCount)
    }

    fun alphaBetaAI(depth: Int): NamedAI {
        return NamedAI("AlphaBeta $depth") { alphaBetaPlay(it, depth) }
    }

    private fun scorerAI(factory: ScorersConfig<TTT3D, TTT3DPoint>): TTT3DAI {
        return lambda@{
            val position = factory.producer(it).score().best().random()
            return@lambda position.x to position.y
        }
    }

    fun aiVs(game: TTT3D, playerX: NamedAI, playerO: NamedAI) {
        print(game)
        while (!game.isGameOver()) {
            if (game.currentPlayer == TTT3DPiece.X) {
                makeMove(game, playerX.ai(game))
            } else {
                makeMove(game, playerO.ai(game))
            }
            this.print(game)
        }
        println(game.findWinner())
    }

/*
floorsCountAverage0: IntSummaryStatistics{count=3228, sum=8823, min=0, average=2.733271, max=7}
threats: IntSummaryStatistics{count=40, sum=677, min=10, average=16.925000, max=26}
winnablesX: IntSummaryStatistics{count=40, sum=434, min=5, average=10.850000, max=19}
winnablesO: IntSummaryStatistics{count=40, sum=434, min=5, average=10.850000, max=19}
net.zomis.spring.games.impls.ur.MonteCarloAI@3590ccd
  moveToFlower: IntSummaryStatistics{count=20, sum=349, min=12, average=17.450000, max=26}
  winDiff: IntSummaryStatistics{count=7, sum=76, min=1, average=10.857143, max=23}
  winResult: 20: 7/0/13 (35.00 %)
  move: IntSummaryStatistics{count=20, sum=1656, min=66, average=82.800000, max=106}
  unfinishedCount: IntSummaryStatistics{count=13, sum=48, min=1, average=3.692308, max=5}
  loseDiff: IntSummaryStatistics{count=13, sum=375, min=1, average=28.846154, max=58}
  knockouted: IntSummaryStatistics{count=20, sum=261, min=7, average=13.050000, max=19}
  piecesInGame: IntSummaryStatistics{count=1656, sum=5094, min=0, average=3.076087, max=7}
  moveFromFlower: IntSummaryStatistics{count=20, sum=338, min=10, average=16.900000, max=26}
  knockouts: IntSummaryStatistics{count=20, sum=173, min=5, average=8.650000, max=15}
  URScorer{name='KFE521T'}
    moveToFlower: IntSummaryStatistics{count=20, sum=349, min=12, average=17.450000, max=26}
    winDiff: IntSummaryStatistics{count=7, sum=76, min=1, average=10.857143, max=23}
    winResult: 20: 7/0/13 (35.00 %)


*/

}

fun main(args: Array<String>) {
//    val game = loadMap("XXO  |      | XO   | OX   /      | OXO  | OOOX |      / XX   | XXO  |      | OO   / OX   |      |      | XX   ")
    val io = TTT3DIO()
    val game = TTT3D()
//    io.aiVs(game, io.alphaBetaAI(3), io.alphaBetaAI(2))
    io.playVsAI(game, io.alphaBetaAI(5), randomTTT3DPiece())
//    io.fight()
}
