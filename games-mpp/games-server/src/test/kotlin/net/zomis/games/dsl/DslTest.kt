package net.zomis.games.dsl

import kotlinx.coroutines.test.runTest
import net.zomis.games.common.Point
import net.zomis.games.dsl.impl.Game
import net.zomis.games.dsl.impl.GameSetupImpl
import net.zomis.games.impl.ttt.DslTTT
import net.zomis.games.impl.ttt.ultimate.TTController
import net.zomis.games.impl.ttt.ultimate.TTPlayer
import net.zomis.games.listeners.BlockingGameListener
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import kotlin.random.Random

class DslTest {

    @Test
    fun config() {
        val configs = GameSetupImpl(DslTTT.game).configs()
        Assertions.assertFalse(configs.isOldStyle())
        Assertions.assertEquals(3, configs.configs.size)
        Assertions.assertTrue(configs.configs.all { it.clazz == Int::class })
        Assertions.assertTrue(configs.configs.all { it.value == 3 })
    }

    class BlockingGameContext<T: Any>(val game: Game<T>, val blocking: BlockingGameListener)

    private fun gameTest(block: suspend BlockingGameContext<TTController>.() -> Unit) = runTest {
        val setup = GameSetupImpl(DslTTT.game)
        val configs = setup.configs()
        configs.set("m", 3)
        configs.set("n", 3)
        configs.set("k", 3)
        val blocking = BlockingGameListener()
        val game = setup.startGameWithConfig(this, 2, configs) {
            listOf(blocking)
        }
        blocking.await()
        block.invoke(BlockingGameContext(game, blocking))
        game.stop()
    }

    @Test
    fun wrongActionType() = gameTest {
        val act = game.actions.type("play") // should be <Point>
        Assertions.assertNotNull(act)
        Assertions.assertThrows(ClassCastException::class.java) {
            val action = act!!.createAction(0, 42)
            act.perform(action)
        }
    }

    @Test
    fun wrongActionName() = gameTest {
        val act = game.actions.type("missing")
        Assertions.assertNull(act)
    }

    @Test
    fun allowedActions() = gameTest {
        Assertions.assertEquals(9, game.actions.type("play")!!.availableActions(0, null).count())
    }

    @Test
    fun makeAMove() = gameTest {
        blocking.awaitAndPerformSerialized(0, "play", Point(1, 2))
        blocking.await()
        Assertions.assertEquals(TTPlayer.X, game.model.game.getSub(1, 2)!!.wonBy)
    }

    @Test
    fun currentPlayerChanges() = gameTest {
        Assertions.assertEquals(0, game.view(0)["currentPlayer"])
        val actionType = game.actions.type("play")!!
        val action = actionType.availableActions(0, null).toList().random()
        actionType.perform(action)
        blocking.await()
        Assertions.assertEquals(1, game.view(0)["currentPlayer"])
    }

    @Test
    fun finishGame() = gameTest {
        var counter = 0
        val random = Random(23)
        while (!game.isGameOver() && counter < 20) {
            val playerIndex = counter % 2
            val actionType = game.actions.type("play")!!
            val availableActions = actionType.availableActions(playerIndex, null)
            Assertions.assertFalse(availableActions.none(), "Game is not over but no available actions after $counter actions")
            val action = availableActions.toList().random(random)
            blocking.awaitAndPerform(playerIndex, "play", action.parameter)
            blocking.await()
            counter++
        }
        Assertions.assertEquals(2, game.eliminations.eliminations().distinctBy { it.playerIndex }.size)
    }

}
