package net.zomis.games.impl.words

import net.zomis.bestOf
import net.zomis.games.WinResult
import net.zomis.games.api.GamesApi
import net.zomis.games.context.Context
import net.zomis.games.context.ContextHolder
import net.zomis.games.context.Entity
import net.zomis.games.dsl.Actionable
import net.zomis.games.dsl.impl.Game
import net.zomis.games.impl.words.wordlists.WordleWords

object Wordle {
    fun evaluate(correct: String, guess: String): Guess {
        val results = guess.map { GuessChar(it, GuessResult.WRONG) }.toMutableList()
        // This naming might be confusing considering the assignments, but it's important to use the other string here
        val remainingCorrectChars = guess.toMutableList()
        val remainingGuessedChars = correct.toMutableList()
        // First check for correct guesses before checking for "bad location"
        for (c in guess.withIndex()) {
            if (c.value == correct[c.index]) {
                results[c.index].result = GuessResult.CORRECT
                remainingCorrectChars.remove(c.value)
                remainingGuessedChars.remove(c.value)
            }
        }
        for (c in remainingGuessedChars) {
            if (c in remainingCorrectChars) {
                remainingCorrectChars.remove(c)
                results.first { it.char == c && it.result == GuessResult.WRONG }.result = GuessResult.BAD_LOCATION
            }
        }
        return Guess(guess, results)
    }
    fun match(word: String, guessResults: List<GuessChar>): Boolean {
        for (c in guessResults.withIndex()) {
            when (c.value.result) {
                GuessResult.WRONG -> {
                    // Word should not contain any *more* of these characters than have already been found
                    val maxCount = guessResults.count { it.char == c.value.char && it.result != GuessResult.WRONG }
                    if (word.count { it == c.value.char } > maxCount) return false
                }
                GuessResult.BAD_LOCATION -> {
                    if (word[c.index] == c.value.char) return false
                    // Word should contain at least X of these characters, but they should not already have been found
                    val minCount = guessResults.count { it.char == c.value.char && it.result != GuessResult.WRONG }
                    if (word.count { it == c.value.char } < minCount) return false
                }
                GuessResult.CORRECT -> {
                    if (word[c.index] != c.value.char) return false
                }
            }
        }
        return true
    }

    enum class GuessResult {
        WRONG,
        BAD_LOCATION,
        CORRECT
    }
    data class GuessChar(val char: Char, var result: GuessResult)
    data class Guess(val word: String, val results: List<GuessChar>)
    class Board(ctx: Context, val playerIndex: Int): Entity(ctx) {
        val guesses by value { mutableListOf<Guess>() }
        val guessAction = action<Model, String>("guess", String::class) {
            precondition { true }
            requires { action.parameter in WordleWords.allGuessable }
            options { WordleWords.allGuessable }
            perform {
                val guess = game.evaluate(action.parameter)
                guesses.add(guess)
                possibleWords.retainAll { match(it, guess.results) }
                if (guess.results.all { it.result == GuessResult.CORRECT }) {
                    eliminations.result(playerIndex, WinResult.WIN)
                }
            }
        }
        val possibleWords by value { WordleWords.choosable.toMutableSet() }.publicView { it.size }
        val score by value { guesses.size }
    }
    class Model(override val ctx: Context): Entity(ctx), ContextHolder {
        fun evaluate(guess: String) = evaluate(correct, guess)
        val all by value { WordleWords.allGuessable.size }
        val guessable by value { WordleWords.guessableOnly.size }
        val possible by value { WordleWords.choosable.size }
        val correct by value { "" }.setup {
            replayable.randomFromList("answer", WordleWords.choosable.toList(), 1) { it }.first()
        }
        val players by playerComponent { Board(ctx, it) }
    }

    fun ai(game: Game<*>, hardMode: Boolean, extendedWordList: Boolean): Actionable<Model, Any> {
        val g = game as Game<Model>
        val board = game.model.players.first()
        // Find best guess...
        // Loop through possible guesses
        // Loop through possible words
        // Group possible words by which results they would give
        // Determine probability and number of words remaining

        // Give some kind of bonus if it is the actual word
        fun weightedSum(groups: Map<List<GuessResult>, Int>): Double {
            val total = board.possibleWords.size.toDouble()
            return groups.map { it.value * it.value / total }.sum()
        }
        fun score(guess: String): Pair<Map<List<GuessResult>, Int>, Double> {
            val groups = board.possibleWords.minus(guess).groupingBy { c -> evaluate(c, guess).results.map { it.result } }.eachCount()
            return groups to weightedSum(groups)
        }
        val words = when {
            hardMode -> board.possibleWords
            extendedWordList -> WordleWords.allGuessable
            else -> WordleWords.choosable
        }
        val chosenGuess = words.bestOf { guess ->
            val score = score(guess)
            println("$guess: ${score.second} - ${score.first}")
            -score.second
        }
        println("best guesses $chosenGuess: ${score(chosenGuess.first())}")
        return game.actions.type(board.guessAction.name)!!.createActionFromSerialized(0, chosenGuess.first())
    }

    val game = GamesApi.gameContext("Wordle", Model::class) {
        players(1..1)
        init { Model(this.ctx) }
        gameFlow {
            loop {
                step("guess") {
                    game.players.forEach {
                        this.enableAction(it.guessAction)
                    }
                }
            }
        }
    }

}