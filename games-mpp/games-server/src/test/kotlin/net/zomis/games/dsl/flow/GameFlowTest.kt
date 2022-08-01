package net.zomis.games.dsl.flow

import kotlinx.coroutines.test.runTest
import net.zomis.games.WinResult
import net.zomis.games.api.GamesApi
import net.zomis.games.dsl.GamesImpl
import net.zomis.games.dsl.listeners.BlockingGameListener
import net.zomis.games.listeners.ReplayListener
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class GameFlowTest {

    class Model

    @Test
    fun `replayable should be possible to use in between steps with actions`() = runTest {
        val factory = GamesApi.gameCreator(Model::class)
        val action = factory.action("stuff", Int::class)
        val spec = factory.game("GameFlowTest") {
            setup {
                init { Model() }
                playersFixed(1)
            }
            gameFlow {
                step("do something") {
                    yieldAction(action) {
                        options { 1..10 }
                    }
                }
                step("between") {
                    // This tests asserts that this "randomness" will be connected with the last made action, and not the next action.
                    replayable.int("test") { 42 }
                }
                step("last action") {
                    yieldAction(action) {
                        options { 1..10 }
                        perform {
                            eliminations.eliminateRemaining(WinResult.WIN)
                        }
                    }
                }
            }
            gameFlowRules {}
        }
        val blocking = BlockingGameListener()
        val replay = ReplayListener(spec.name)
        val game = GamesImpl.game(spec).setup().startGame(this, 1) {
            listOf(blocking, replay)
        }
        blocking.await()
        blocking.awaitAndPerform(0, action, 1)
        blocking.await()
        blocking.awaitAndPerform(0, action, 2)
        blocking.awaitGameEnd()
        val data = replay.data()
        val action0 = data.actions[0]
        Assertions.assertEquals(1, action0.state.size)
        val action1 = data.actions[1]
        Assertions.assertEquals(0, action1.state.size)

        Assertions.assertEquals(42, action0.state.getValue("test"))
    }

}
