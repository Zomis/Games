package net.zomis.games.dsl.games

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import net.zomis.games.WinResult
import net.zomis.games.common.Point
import net.zomis.games.dsl.GameAsserts
import net.zomis.games.dsl.impl.Game
import net.zomis.games.dsl.impl.GameSetupImpl
import net.zomis.games.impl.ttt.DslTTT
import net.zomis.games.impl.ttt.ultimate.TTController
import net.zomis.games.dsl.listeners.BlockingGameListener
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class TTTTest {

    val dsl = DslTTT.game
    lateinit var game: Game<TTController>
    lateinit var test: GameAsserts<TTController>
    lateinit var blocking: BlockingGameListener

    suspend fun setup(scope: CoroutineScope) {
        val setup = GameSetupImpl(dsl)
        blocking = BlockingGameListener()
        game = setup.startGame(scope, 2) { listOf(blocking) }
        test = GameAsserts(game, blocking)
    }

    @Test
    fun draw() = runTest {
        setup(this)
        blocking.await()
        val play = DslTTT.playAction.name
        test.performActionSerialized(0, play, Point(1, 1))
        test.performActionSerialized(1, play, Point(0, 0))
        test.performActionSerialized(0, play, Point(0, 1))
        test.performActionSerialized(1, play, Point(2, 1))
        test.performActionSerialized(0, play, Point(1, 0))
        test.performActionSerialized(1, play, Point(2, 0))
        test.performActionSerialized(0, play, Point(2, 2))
        test.performActionSerialized(1, play, Point(1, 2))
        blocking.await()
        Assertions.assertEquals(2, game.eliminations.remainingPlayers().size)
        // Game situation, next move will result in draw:
        //      100
        //      001
        //      _11
        test.performActionSerialized(0, play, Point(0, 2))
        blocking.await()
        Assertions.assertEquals(0, game.eliminations.remainingPlayers().size)
        Assertions.assertEquals(2, game.eliminations.eliminations().size)
        Assertions.assertTrue(game.eliminations.eliminations().all { it.winResult == WinResult.DRAW }) {
            game.eliminations.eliminations().toString()
        }
        blocking.await()
        test.game.stop()
    }

}
