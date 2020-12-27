package net.zomis.games.impl.words.wordlists

import net.zomis.games.dsl.ReplayableScope

class WordList(val words: List<String>) {

    fun chooseX(replayable: ReplayableScope, key: String, amount: Int): List<String>
        = replayable.strings(key) { words.shuffled().take(amount) }
    fun chooseXY(replayable: ReplayableScope, key: String, amountA: Int, amountB: Int): Pair<List<String>, List<String>> {
        val shuffled = words.shuffled()
        val chosen = replayable.strings(key) { shuffled.take(amountA) + shuffled.takeLast(amountB) }
        val first = chosen.take(amountA)
        val second = chosen.takeLast(amountB)
        return first to second
    }

}

fun String.toWordList(): WordList = WordList(this.trim().split("\n").map { it.trim() })

