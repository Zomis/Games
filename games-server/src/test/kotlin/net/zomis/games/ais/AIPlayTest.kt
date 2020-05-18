package net.zomis.games.ais

import net.zomis.games.dsl.impl.GameControllerContext
import net.zomis.games.dsl.impl.GameSetupImpl
import net.zomis.games.impl.HanabiGame
import net.zomis.games.server2.ais.AIFactoryScoring
import net.zomis.games.server2.ais.gamescorers.HanabiScorers

class AIPlayTest {

    fun hanabi() {
        val ai = HanabiScorers.aiFirst()
        val config = ai.config.toList()
        val controller = AIFactoryScoring().createController(config)
        val setup = GameSetupImpl(HanabiGame.game)
        val game = setup.createGame(2, setup.getDefaultConfig())
        var moveCount = 0
        while (!game.isGameOver()) {
            val controllerContext = GameControllerContext(game, game.model.currentPlayer)
            val move = controller(controllerContext)!!
            game.actions.type(move.actionType)!!.perform(move)
            moveCount++
        }
        println("${game.model.score()} after $moveCount moves")
    }

}

fun main() {
    AIPlayTest().hanabi()
}
