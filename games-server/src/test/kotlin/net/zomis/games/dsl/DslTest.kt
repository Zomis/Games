package net.zomis.games.dsl

import net.zomis.games.dsl.impl.GameImpl
import net.zomis.games.dsl.impl.GameSetupImpl
import net.zomis.tttultimate.TTBase
import net.zomis.tttultimate.TTPlayer
import net.zomis.tttultimate.games.TTController
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import kotlin.random.Random

class DslTest {

    @Test
    fun config() {
        val setup = GameSetupImpl(DslTTT().game)
        Assertions.assertEquals(TTOptions::class, setup.configClass())
    }

    private fun createGame(): GameImpl<TTController> {
        val setup = GameSetupImpl(DslTTT().game)
        val game = setup.createGame(TTOptions(3, 3, 3))
        Assertions.assertNotNull(game)
        return game
    }

    @Test
    fun allowedActions() {
        val game = createGame()
        Assertions.assertEquals(9, game.availableActions<TTBase>("play", 0).count())
    }

    @Test
    fun makeAMove() {
        val game = createGame()
        val actions = game.actionType<Point>("play")
        val action = actions.createAction(0, game.model, Point(1, 2))
        actions.performAction(action)
        Assertions.assertEquals(TTPlayer.X, game.model.game.getSub(1, 2)!!.wonBy)
    }

    @Test
    fun currentPlayerChanges() {
        val game = createGame()
        Assertions.assertEquals(0, game.view(0)["currentPlayer"])
        val action = game.availableActions<TTBase>("play", 0).toList().random()
        game.performAction("play", action)
        Assertions.assertEquals(1, game.view(0)["currentPlayer"])
    }

    @Test
    fun finishGame() {
        val game = createGame()
        var counter = 0
        val random = Random(23)
        while (game.view(0)["winner"] == null && counter < 20) {
            val playerIndex = counter % 2
            val availableActions = game.availableActions<TTBase>("play", playerIndex)
            Assertions.assertFalse(availableActions.none(), "Game is not over but no available actions after $counter actions")
            val action = availableActions.toList().random(random)
            game.performAction("play", action)
            counter++
        }
        Assertions.assertNotNull(game.view(0)["winner"])
    }

}