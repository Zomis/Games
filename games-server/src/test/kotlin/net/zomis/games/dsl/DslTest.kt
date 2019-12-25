package net.zomis.games.dsl

import net.zomis.games.dsl.impl.GameImpl
import net.zomis.games.dsl.impl.GameSetupImpl
import net.zomis.tttultimate.TTBase
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
        Assertions.assertEquals(9, game.availableActions<TTBase>("play", 0).size)
    }

    @Test
    fun makeAMove() {
        val game = createGame()
        val action = Action2D(game.model, 0, 1, 2, game.model.game.getSub(1, 2))
        Assertions.assertTrue(game.performAction("play", action))
    }

    @Test
    fun currentPlayerChanges() {
        val game = createGame()
        Assertions.assertEquals(0, game.view()["currentPlayer"])
        val action = game.availableActions<TTBase>("play", 0).random()
        game.performAction("play", action)
        Assertions.assertEquals(1, game.view()["currentPlayer"])
    }

    @Test
    fun finishGame() {
        val game = createGame()
        var counter = 0
        val random = Random(23)
        while (game.view()["winner"] == null && counter < 20) {
            val playerIndex = counter % 2
            val availableActions = game.availableActions<TTBase>("play", playerIndex)
            Assertions.assertFalse(availableActions.isEmpty(), "Game is not over but no available actions after $counter actions")
            val action = availableActions.random(random)
            game.performAction("play", action)
            counter++
        }
        Assertions.assertNotNull(game.view()["winner"])
    }

}