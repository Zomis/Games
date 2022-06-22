package net.zomis.games.dsl.replays

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import net.zomis.games.common.Point
import net.zomis.games.dsl.GamesImpl
import net.zomis.games.dsl.flow.runBlocking
import net.zomis.games.dsl.impl.GameController
import net.zomis.games.dsl.startSynchronized
import net.zomis.games.impl.DslSplendor
import net.zomis.games.impl.ttt.DslTTT
import net.zomis.games.listeners.PlayerController
import net.zomis.games.listeners.ReplayListener
import net.zomis.games.server2.ais.gamescorers.SplendorScorers
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class FirstReplayTest {

    @Test
    fun `Deterministic Tic-Tac-Toe game`() {
        val entryPoint = GamesImpl.game(DslTTT.game)
        val replayStore = entryPoint.inMemoryReplay()
        val gameplay = entryPoint.replayable(2, entryPoint.setup().configs(), replayStore).runBlocking()
        gameplay.game.startSynchronized()
        gameplay.actionSerialized(0, DslTTT.playAction, Point(0, 0))
        gameplay.actionSerialized(1, DslTTT.playAction, Point(1, 1))
        gameplay.actionSerialized(0, DslTTT.playAction, Point(2, 2))
        gameplay.actionSerialized(1, DslTTT.playAction, Point(1, 0))
        gameplay.actionSerialized(0, DslTTT.playAction, Point(1, 2))
        gameplay.actionSerialized(1, DslTTT.playAction, Point(0, 2))
        gameplay.actionSerialized(0, DslTTT.playAction, Point(2, 0))
        gameplay.actionSerialized(1, DslTTT.playAction, Point(2, 1))
        gameplay.actionSerialized(0, DslTTT.playAction, Point(0, 1))
        Assertions.assertTrue(gameplay.game.isGameOver())
        val view = gameplay.game.view(0)
        println(replayStore.data().actions)

        runBlocking {
            val replay = entryPoint.replay(replayStore.data())
            replay.game.startSynchronized()
            replay.goToEnd()
            Assertions.assertTrue(replay.game.isGameOver())
            Assertions.assertEquals(view, replay.game.view(0))
        }
    }

    @Test
    fun `Game with AI and replayable randomness`() = runTest {
        val entryPoint = GamesImpl.game(DslSplendor.splendorGame)
        val replayListener = ReplayListener(entryPoint.gameType)
        val controller = SplendorScorers.aiBuyFirst.createController() as GameController<Any>
        val game = entryPoint.setup().startGame(this, 3) {
            listOf(
                replayListener,
                PlayerController(it, 0..2, controller),
            )
        }
        while (!game.isGameOver()) {
            delay(100)
        }

        val replayData = replayListener.data()
        val view = game.view(0)
        runBlocking {
            val replay = entryPoint.replay(replayData)
            Assertions.assertTrue(replayData.initialState!!.isNotEmpty()) { "No initial state" }
            Assertions.assertTrue(replayData.actions.isNotEmpty()) { "No actions!" }
            replay.game.startSynchronized()
            replay.goToEnd()
            Assertions.assertTrue(replay.game.isGameOver()) { "Replayed game didn't finish properly" }
            Assertions.assertEquals(view, replay.game.view(0))
        }
    }

}