package net.zomis.games.dsl.replays

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import net.zomis.games.common.Point
import net.zomis.games.dsl.*
import net.zomis.games.dsl.impl.Game
import net.zomis.games.dsl.impl.GameAI
import net.zomis.games.dsl.impl.GameController
import net.zomis.games.dsl.listeners.BlockingGameListener
import net.zomis.games.impl.DslSplendor
import net.zomis.games.impl.SplendorGame
import net.zomis.games.impl.ttt.DslTTT
import net.zomis.games.impl.ttt.ultimate.TTController
import net.zomis.games.listeners.PlayerController
import net.zomis.games.listeners.ReplayListener
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class FirstReplayTest {

    private suspend fun <T: Any, P: Any> Game<T>.actionSerialized(playerIndex: Int, actionType: ActionType<T, P>, param: Any) {
        val action = this.actions.type(actionType)!!.createActionFromSerialized(playerIndex, param) as Actionable<T, P>
        this.actionsInput.send(action)
    }

    @Test
    fun `Deterministic Tic-Tac-Toe game`() = runTest {
        val entryPoint = GamesImpl.game(DslTTT.game)
        val replayListener = ReplayListener(entryPoint.gameType)
        val gameplay = entryPoint.setup().startGame(this, 2) {
            listOf(replayListener)
        }
        gameplay.actionSerialized(0, DslTTT.playAction, Point(0, 0))
        gameplay.actionSerialized(1, DslTTT.playAction, Point(1, 1))
        gameplay.actionSerialized(0, DslTTT.playAction, Point(2, 2))
        gameplay.actionSerialized(1, DslTTT.playAction, Point(1, 0))
        gameplay.actionSerialized(0, DslTTT.playAction, Point(1, 2))
        gameplay.actionSerialized(1, DslTTT.playAction, Point(0, 2))
        gameplay.actionSerialized(0, DslTTT.playAction, Point(2, 0))
        gameplay.actionSerialized(1, DslTTT.playAction, Point(2, 1))
        gameplay.actionSerialized(0, DslTTT.playAction, Point(0, 1))
        while (gameplay.isRunning()) delay(100)

        ConsoleView<TTController>().showView(gameplay, 0)
        val view = gameplay.view(0)

        Assertions.assertTrue(gameplay.isGameOver())
        Assertions.assertEquals(9, replayListener.data().actions.size)

        val replay = entryPoint.replay(this, replayListener.data()).goToEnd().awaitCatchUp()
        Assertions.assertTrue(replay.game.isGameOver())
        Assertions.assertEquals(view, replay.game.view(0))
    }

    @Test
    fun `Game with AI and replayable randomness`() = runTest {
        val entryPoint = GamesImpl.game(DslSplendor.splendorGame)
        val replayListener = ReplayListener(entryPoint.gameType)
        val controller = entryPoint.setup().findAI("#AI_BuyFirst") as GameAI<SplendorGame>
        val blocking = BlockingGameListener()
        val game = entryPoint.setup().startGame(this, 3) {
            listOf(
                blocking,
                replayListener,
                controller.gameListener(it as Game<SplendorGame>, 0),
                controller.gameListener(it as Game<SplendorGame>, 1),
                controller.gameListener(it as Game<SplendorGame>, 2)
            )
        }
        blocking.awaitGameEnd()

        Assertions.assertTrue(game.isGameOver())
        val replayData = replayListener.data()
        Assertions.assertTrue(replayData.initialState!!.isNotEmpty())
        Assertions.assertTrue(replayData.actions.any { it.state.isNotEmpty() })

        val view = game.view(0)

        val replay = entryPoint.replay(this, replayData)
        Assertions.assertTrue(replayData.initialState!!.isNotEmpty()) { "No initial state" }
        Assertions.assertTrue(replayData.actions.isNotEmpty()) { "No actions!" }
        println("Cards on start: " + replay.game.model.board.cards)
        replay.goToEnd().awaitCatchUp()

        Assertions.assertTrue(replay.game.isGameOver()) { "Replayed game didn't finish properly" }
        Assertions.assertEquals(view, replay.game.view(0))
    }

}