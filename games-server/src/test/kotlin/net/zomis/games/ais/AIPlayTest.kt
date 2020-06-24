package net.zomis.games.ais

import net.zomis.games.dsl.impl.GameControllerContext
import net.zomis.games.dsl.impl.GameSetupImpl
import net.zomis.games.impl.DslSplendor
import net.zomis.games.server2.ais.gamescorers.SplendorScorers

class AIPlayTest {

    fun hanabi() {
//        val gameAndAI = HanabiGame.game to HanabiScorers.aiSecond()
        val gameAndAI = DslSplendor.splendorGame to SplendorScorers.aiBuyFirst

        val controller = gameAndAI.second.createController()
        val setup = GameSetupImpl(gameAndAI.first)
        val game = setup.createGame(2, setup.getDefaultConfig())
        var moveCount = 0
        while (!game.isGameOver()) {
            val controllerContext = GameControllerContext(game, game.model.currentPlayerIndex)
            val move = controller(controllerContext)!!
            println("Move $moveCount: $move")
            game.actions.type(move.actionType)!!.perform(move)
            moveCount++
        }
        println("${game.model.roundNumber} after $moveCount moves")
    }

}

fun main() {
    AIPlayTest().hanabi()
}
