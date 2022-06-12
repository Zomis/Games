package net.zomis.games.standalone

import net.zomis.bestOf
import net.zomis.games.dsl.GameConfigs
import net.zomis.games.dsl.GamesImpl
import net.zomis.games.impl.words.Wordle
import net.zomis.games.impl.words.wordlists.WordleWords

fun main() {
    val count = 1

    val games = (1..count).map { GamesImpl.game(Wordle.game) }.map {
        it.replayable(1, GameConfigs(emptyList()))
    }

    fun add(index: Int, results: Pair<String, IntArray>) {
        games[index].game.model.players.first().guesses.add(Wordle.guessResult(results.first, *results.second))
    }
    fun add(guess: String, results: List<IntArray?>) {
        results.forEachIndexed { index, intArray ->
            if (intArray != null)
                games[index].game.model.players.first().guesses.add(Wordle.guessResult(guess, *intArray))
        }
    }

//    add(0, "_____" to intArrayOf(0,2,0,2,2))
//    add("_____", listOf(intArrayOf(0,0,0,1,0), intArrayOf(0,0,0,1,0), intArrayOf(0,0,0,1,0), intArrayOf(1,0,0,0,0)))

    games.forEach { g ->
        g.game.model.players.first().also {
            it.guesses.forEach { guess -> it.reducePossibleWords(guess) }
        }
    }
//    val words = if (true) games else WordleWords.choosable
    val guess = WordleWords.choosable.bestOf { guess ->
        games.map { it.game.model.players.first() }.filter { !it.completed() }.sumOf {
            val possibleBonus = if (guess in it.possibleWords) 1 else 0
            val score = Wordle.Bot.score(it, guess)
            println("$guess: ${score.second}")
//            println("$guess: ${score.second} - ${score.first}")
            -score.second + possibleBonus
        }
    }
    games.forEach {
        val possible = it.game.model.players.first().possibleWords
        println("${possible.size} Possible: $possible")
    }
    println(guess)
}
