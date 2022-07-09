package net.zomis.games.ais

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import net.zomis.games.dsl.impl.GameControllerContext
import net.zomis.games.dsl.impl.GameSetupImpl
import net.zomis.games.dsl.listeners.BlockingGameListener
import net.zomis.games.impl.DslSplendor
import net.zomis.games.server2.ais.gamescorers.SplendorScorers

class AIPlayTest {

    suspend fun hanabi(coroutineScope: CoroutineScope) {
//        val gameAndAI = HanabiGame.game to HanabiScorers.aiSecond()
        val gameAndAI = DslSplendor.splendorGame to SplendorScorers.aiBuyFirst

        val controller = gameAndAI.second.createController()
        val setup = GameSetupImpl(gameAndAI.first)
        val blocking = BlockingGameListener()
        val game = setup.startGame(coroutineScope, 2) {
            listOf(blocking)
        }
        blocking.await()
        var moveCount = 0
        while (!game.isGameOver()) {
            val controllerContext = GameControllerContext(game, game.model.currentPlayerIndex)
            val move = controller(controllerContext)!!
            println("Move $moveCount: $move")
            blocking.awaitAndPerform(move)
            moveCount++
            blocking.await()
        }
        println("${game.model.roundNumber} after $moveCount moves")
    }
}

suspend fun main() {
    coroutineScope {
        AIPlayTest().hanabi(this)
    }
}
