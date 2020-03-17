package net.zomis.games.ais

import net.zomis.games.server2.games.impl.TTAlphaBeta
import net.zomis.games.server2.games.impl.TTConnect4AlphaBeta
import net.zomis.tttultimate.TTPlayer
import net.zomis.tttultimate.TTWinCondition
import net.zomis.tttultimate.Winnable
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.streams.toList

class Connect4MissingTest {

    private enum class Winner : Winnable {
        NONE, X, O, XO, BLOCKED;

        override val wonBy: TTPlayer
            get() = TTPlayer.valueOf(this.toString())
    }

    @CsvSource(value = [
        "3,X,X____________X",
        "2,X,XX_________",
        "3,X,_X_O__X_",
        "3,X,XX_O__X_",
        "3,X,__X_O__",
        "3,X,__X_",
        "-,X,__XO___",
        "-,X,X__O___",
        "0,X,XXXXOOO"
    ])
    @ParameterizedTest(name = "{2} should return {0} for {1}")
    fun test(expected: String, player: String, row: String) {
        val required = 4
        val ttPlayer = TTPlayer.valueOf(player)
        val winCondition = TTWinCondition(row.chars().mapToObj {c ->
            val w = Winner.values().find { it.name == c.toChar().toString() } ?: Winner.NONE
            w
        }.toList(), required)
        Assertions.assertEquals(row.length, winCondition.size())
        val result = TTConnect4AlphaBeta.missingForWin(winCondition, ttPlayer, required)
        if (expected == "-") {
            Assertions.assertNull(result)
        } else {
            Assertions.assertEquals(expected.toInt(), result)
        }
    }


}