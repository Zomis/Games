package net.zomis.games.dsl.replays

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import net.zomis.games.api.GamesApi
import net.zomis.games.common.Point
import net.zomis.games.dsl.*
import net.zomis.games.dsl.impl.Game
import net.zomis.games.dsl.impl.GameAI
import net.zomis.games.dsl.listeners.BlockingGameListener
import net.zomis.games.impl.DslSplendor
import net.zomis.games.impl.SplendorGame
import net.zomis.games.impl.ttt.DslTTT
import net.zomis.games.impl.ttt.ultimate.TTController
import net.zomis.games.listeners.ReplayListener
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class FirstReplayTest {

    private suspend fun <T: Any, P: Any> Game<T>.actionSerialized(playerIndex: Int, actionType: ActionType<T, P>, param: Any) {
        val action = this.actions.type(actionType)!!.createActionFromSerialized(playerIndex, param)
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
    fun `Randomness without actions should also work`() = runTest {
        var times = 1
        data class RandomnessWithoutActions(var value: Int)
        val factory = GamesApi.gameCreator(RandomnessWithoutActions::class).game("RandomnessTest") {
            setup {
                playersFixed(1)
                init { RandomnessWithoutActions(0) }
                onStart {
                    game.value = this.replayable.int("start") { times++ }
                }
            }
        }
        val blocking = BlockingGameListener()
        val replay = ReplayListener("RandomnessTest")
        val game = GamesImpl.game(factory).setup().startGame(this, 1) {
            listOf(replay, blocking)
        }
        blocking.await()
        println("Game ready: ${game.model}")

        val replayData = replay.data()
        println("Replay data: $replayData")
        val blocking2 = BlockingGameListener()
        val copy = GamesImpl.game(factory).replay(this, replayData, gameListeners = {
            listOf(blocking2)
        }).goToEnd().awaitCatchUp()
        println("Game Copy ready: ${copy.game.model}")
        blocking2.await()
        println("Game Copy ready2: ${copy.game.model}")
        Assertions.assertEquals(game.model.value, copy.game.model.value)
        game.stop()
        copy.game.stop()
    }

    @Test
    fun `Game with AI and replayable randomness`() = runTest {
        val entryPoint = GamesImpl.game(DslSplendor.splendorGame)
        val replayListener = ReplayListener(entryPoint.gameType)
        val gameAI = entryPoint.setup().findAI("#AI_BuyFirst") as GameAI<SplendorGame>
        val blocking = BlockingGameListener()
        val game = entryPoint.setup().startGame(this, 3) {
            listOf(
                blocking,
                replayListener,
            ) + (it.playerIndices).map { playerIndex -> gameAI.gameListener(it as Game<SplendorGame>, playerIndex) }
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