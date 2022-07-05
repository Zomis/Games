package net.zomis.games.dsl

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import net.zomis.games.common.Point
import net.zomis.games.common.PointMove
import net.zomis.games.impl.TTControllerSourceDestination
import net.zomis.games.impl.TTSourceDestinationGames
import net.zomis.games.impl.ttt.ultimate.TTPlayer
import net.zomis.games.dsl.listeners.BlockingGameListener
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class QuixoTest {

    private suspend fun createTestGame(scope: CoroutineScope): GameAsserts<TTControllerSourceDestination> {
        val blocking = BlockingGameListener()
        val game = GamesImpl.game(TTSourceDestinationGames.gameQuixo).setup().startGame(scope, 2) {
            listOf(blocking)
        }
        blocking.await()
        return GameAsserts(game, blocking)
    }
    private val move = "move"

    @Test
    fun firstMovePossibles() = runTest {
        val testGame = createTestGame(this)
        testGame.expectPossibleOptions(0, move, 16)
        testGame.expectPossibleOptions(0, move, 3, Point(0, 1))
        testGame.expectPossibleOptions(0, move, 2, Point(0, 0))
        testGame.game.stop()
    }

    @Test
    fun onlyOnePlayerOptionsAllowed() = runTest {
        val testGame = createTestGame(this)
        testGame.expectPossibleOptions(1, move, 0)
        testGame.game.stop()
    }

    @Test
    fun onlyOnePlayerActionAllowed() = runTest {
        val testGame = createTestGame(this)
        testGame.expectPossibleActions(1, move, 0)
        testGame.game.stop()
    }

    @Test
    fun secondMoveShouldMoveStuff() = runTest {
        val testGame = createTestGame(this)
        testGame.performAction(0, move, PointMove(Point(0, 1), Point(0, 0)))
        testGame.performAction(1, move, PointMove(Point(0, 2), Point(0, 0)))
        Assertions.assertEquals(TTPlayer.X, testGame.game.model.board.getSub(0, 1)!!.wonBy)
        Assertions.assertEquals(TTPlayer.O, testGame.game.model.board.getSub(0, 0)!!.wonBy)
        testGame.game.stop()
    }

}