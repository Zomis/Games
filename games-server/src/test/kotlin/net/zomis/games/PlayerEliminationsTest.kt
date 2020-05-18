package net.zomis.games

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class PlayerEliminationsTest {

    private fun PlayerEliminations.scoreList() =
        this.eliminations().sortedBy { it.playerIndex }.map { it.winResult to it.position }

    @Test
    fun simpleElimination() {
        val elims = PlayerEliminations(2)
        Assertions.assertFalse(elims.isGameOver())

        elims.result(0, WinResult.WIN)
        Assertions.assertFalse(elims.isGameOver())
        Assertions.assertEquals(listOf(1), elims.remainingPlayers())

        elims.result(1, WinResult.LOSS)
        Assertions.assertTrue(elims.isGameOver())
        Assertions.assertEquals(listOf(WinResult.WIN to 1, WinResult.LOSS to 2), elims.scoreList())
    }

    @Test
    fun complexElimination() {
        val elims = PlayerEliminations(5)
        elims.result(2, WinResult.WIN)
        elims.result(3, WinResult.LOSS)
        Assertions.assertEquals(2, elims.nextEliminationPosition(WinResult.WIN))
        Assertions.assertEquals(2, elims.nextEliminationPosition(WinResult.DRAW))
        Assertions.assertEquals(4, elims.nextEliminationPosition(WinResult.LOSS))
        elims.eliminateRemaining(WinResult.WIN)

        Assertions.assertEquals(listOf(
            WinResult.WIN to 2,
            WinResult.WIN to 2,
            WinResult.WIN to 1,
            WinResult.LOSS to 5,
            WinResult.WIN to 2
        ), elims.scoreList())
    }

    @Test
    fun draw() {
        val elims = PlayerEliminations(2)
        val scores = listOf(0 to 20, 1 to 20)
        val comparator = compareBy<Int> { it }
        elims.eliminateBy(scores, comparator)
        Assertions.assertTrue(elims.scoreList().all { it.first == WinResult.DRAW }) { elims.scoreList().toString() }
    }

    @Test
    fun scoreElimination() {
        val elims = PlayerEliminations(2)
        val scores = listOf(0 to 42, 1 to 23)
        val comparator = compareBy<Int> { it }
        elims.eliminateBy(scores, comparator)

        Assertions.assertEquals(listOf(
            WinResult.WIN to 1,
            WinResult.LOSS to 2
        ), elims.scoreList())
    }

    @Test
    fun scoreElimination8() {
        val elims = PlayerEliminations(8)
        val scores = listOf(0 to 4, 1 to 6, 2 to 10, 3 to 6, 4 to 3, 5 to 10, 6 to 1, 7 to 8 )
        val comparator = compareBy<Int> { it }
        elims.eliminateBy(scores, comparator)

        Assertions.assertEquals(listOf(
            WinResult.DRAW to 6,
            WinResult.DRAW to 4,
            WinResult.WIN to 1,
            WinResult.DRAW to 4,
            WinResult.DRAW to 7,
            WinResult.WIN to 1,
            WinResult.LOSS to 8,
            WinResult.DRAW to 3
        ), elims.scoreList())
    }

    // 1 2 3 4 - one at a time
    // 1 1 2 2 -
    // 1 1 3 2 - loser, loser with position, double win
    // 1 1 3 2 - loser, loser with position, draw
    // 4 3 2 1 - one at a time, different order

}