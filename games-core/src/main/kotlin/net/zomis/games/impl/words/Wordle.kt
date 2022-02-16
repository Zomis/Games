package net.zomis.games.impl.words

import net.zomis.games.WinResult
import net.zomis.games.api.GamesApi
import net.zomis.games.context.Context
import net.zomis.games.context.ContextHolder
import net.zomis.games.context.Entity
import net.zomis.games.impl.words.wordlists.WordleWords

object Wordle {
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
                if (guess.results.all { it.result == GuessResult.CORRECT }) eliminations.result(playerIndex, WinResult.WIN)
            }
        }
    }
    class Model(override val ctx: Context): Entity(ctx), ContextHolder {
        fun evaluate(parameter: String): Guess {
            val results = parameter.map { GuessChar(it, GuessResult.WRONG) }.toMutableList()
            val remainingCorrectChars = parameter.toMutableList()
            val remainingGuessedChars = correct.toMutableList()
            // 1. Check for correct guesses
            for (c in parameter.withIndex()) {
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
            return Guess(parameter, results)
        }
        val all by value { WordleWords.allGuessable.size }
        val guessable by value { WordleWords.guessableOnly.size }
        val possible by value { WordleWords.choosable.size }
        val correct by value { "" }.setup {
            replayable.randomFromList("answer", WordleWords.choosable.toList(), 1) { it }.first()
        }
        val players by playerComponent { Board(ctx, it) }
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