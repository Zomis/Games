package net.zomis.games.dsl

import net.zomis.games.common.Point
import net.zomis.games.common.PointMove
import net.zomis.games.impl.TTQuixoController
import net.zomis.tttultimate.TTPlayer
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class QuixoTest {

    private val createTestGame = { TestGame.create<TTQuixoController>("Quixo") }
    private val move = "move"

    @Test
    fun firstMovePossibles() {
        val testGame = createTestGame()
        testGame.expectPossibleOptions(0, move, 16)
        testGame.expectPossibleOptions(0, move, 3, Point(0, 1))
        testGame.expectPossibleOptions(0, move, 2, Point(0, 0))
    }

    @Test
    fun onlyOnePlayerOptionsAllowed() {
        createTestGame().expectPossibleOptions(1, move, 0)
    }

    @Test
    fun onlyOnePlayerActionAllowed() {
        createTestGame().expectPossibleActions(1, move, 0)
    }

    @Test
    fun secondMoveShouldMoveStuff() {
        val testGame = createTestGame()
        testGame.performAction(0, move, PointMove(Point(0, 1), Point(0, 0)))
        testGame.performAction(1, move, PointMove(Point(0, 2), Point(0, 0)))
        Assertions.assertEquals(TTPlayer.X, testGame.game.model.board.getSub(0, 1)!!.wonBy)
        Assertions.assertEquals(TTPlayer.O, testGame.game.model.board.getSub(0, 0)!!.wonBy)
    }

}