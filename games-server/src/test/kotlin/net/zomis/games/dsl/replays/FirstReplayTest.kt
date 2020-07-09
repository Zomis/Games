package net.zomis.games.dsl.replays

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.zomis.games.common.Point
import net.zomis.games.dsl.GamesImpl
import net.zomis.games.impl.DslSplendor
import net.zomis.games.impl.ttt.DslTTT
import net.zomis.games.server.GamesServer
import net.zomis.games.server2.ais.gamescorers.SplendorScorers
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.File

class FirstReplayTest {

    @Test
    fun ttt() {
        val entryPoint = GamesImpl.game(DslTTT.game)
        val replayStore = entryPoint.inMemoryReplay()
        val gameplay = entryPoint.replayable(2, null, replayStore)
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

        val replay = entryPoint.replay(replayStore.data())
        replay.goToEnd()
        Assertions.assertTrue(replay.game.isGameOver())
        Assertions.assertEquals(view, replay.game.view(0))
    }

    @Test
    fun gameWithAI() {
        val entryPoint = GamesImpl.game(DslSplendor.splendorGame)
        val replayStore = entryPoint.inMemoryReplay()
        val play = entryPoint.replayable(3, null, replayStore)
        val controller = SplendorScorers.aiBuyFirst.createController()
        play.playThroughWithControllers { controller }

        val replayData = replayStore.data()
        val view = play.game.view(0)
        val replay = entryPoint.replay(replayData)
        replay.goToEnd()
        Assertions.assertTrue(replay.game.isGameOver())
        Assertions.assertEquals(view, replay.game.view(0))
    }

}