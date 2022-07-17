package net.zomis.games.ais

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import net.zomis.games.dsl.impl.GameControllerContext
import net.zomis.games.dsl.impl.GameSetupImpl
import net.zomis.games.dsl.listeners.BlockingGameListener
import net.zomis.games.impl.DslSplendor

class AIPlayTest {

    suspend fun playTest(coroutineScope: CoroutineScope) {
//        val gameAndAI = HanabiGame.game to HanabiScorers.aiSecond()
        val gameAndAI = DslSplendor.splendorGame to "#AI_BuyFirst"
        val setup = GameSetupImpl(gameAndAI.first)
        val controller = setup.findAI(gameAndAI.second)
            ?: throw IllegalArgumentException("No AI found: ${gameAndAI.second} for game ${gameAndAI.first.name}")

        val blocking = BlockingGameListener()
        val game = setup.startGame(coroutineScope, 2) {
            listOf(blocking)
        }
        blocking.await()
        var moveCount = 0
        while (!game.isGameOver()) {
            val move = controller.simpleAction(game, game.model.currentPlayerIndex)!!
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
        AIPlayTest().playTest(this)
    }
}
