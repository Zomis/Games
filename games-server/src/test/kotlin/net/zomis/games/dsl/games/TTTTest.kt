package net.zomis.games.dsl.games

import net.zomis.games.WinResult
import net.zomis.games.common.Point
import net.zomis.games.dsl.DslTTT
import net.zomis.games.dsl.GameTest
import net.zomis.games.dsl.impl.GameImpl
import net.zomis.games.dsl.impl.GameSetupImpl
import net.zomis.games.impl.SkullGameModel
import net.zomis.tttultimate.games.TTController
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TTTTest {

    val dsl = DslTTT().game
    lateinit var game: GameImpl<TTController>
    lateinit var test: GameTest<TTController>

    @BeforeEach
    fun setup() {
        val setup = GameSetupImpl(dsl)
        game = setup.createGame(2, setup.getDefaultConfig())
        test = GameTest(game)
    }

    @Test
    fun draw() {
        val play = DslTTT().playAction.name
        test.performActionSerialized(0, play, Point(1, 1))
        test.performActionSerialized(1, play, Point(0, 0))
        test.performActionSerialized(0, play, Point(0, 1))
        test.performActionSerialized(1, play, Point(2, 1))
        test.performActionSerialized(0, play, Point(1, 0))
        test.performActionSerialized(1, play, Point(2, 0))
        test.performActionSerialized(0, play, Point(2, 2))
        test.performActionSerialized(1, play, Point(1, 2))
        Assertions.assertEquals(2, game.eliminationCallback.remainingPlayers().size)
        // Game situation, next move will result in draw:
        //      100
        //      001
        //      _11
        test.performActionSerialized(0, play, Point(0, 2))
        Assertions.assertEquals(0, game.eliminationCallback.remainingPlayers().size)
        Assertions.assertEquals(2, game.eliminationCallback.eliminations().size)
        Assertions.assertTrue(game.eliminationCallback.eliminations().all { it.winResult == WinResult.DRAW }) {
            game.eliminationCallback.eliminations().toString()
        }
    }

}
