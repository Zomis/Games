package net.zomis.games.dsl.games

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import net.zomis.games.dsl.GameAsserts
import net.zomis.games.dsl.impl.FlowStep
import net.zomis.games.dsl.impl.Game
import net.zomis.games.dsl.impl.GameSetupImpl
import net.zomis.games.impl.SkullCard
import net.zomis.games.impl.SkullGame
import net.zomis.games.impl.SkullGameModel
import net.zomis.games.listeners.BlockingGameListener
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

suspend fun Game<*>.awaitInput(coroutineScope: CoroutineScope) {
    val game = this
    coroutineScope.launch {
        for (i in game.feedbackFlow) {
            println("AwaitInput Feedback: $i")
            if (i is FlowStep.ProceedStep) break
        }
    }
}

class SkullTest {

    val dsl = SkullGame.game
    lateinit var game: Game<SkullGameModel>
    lateinit var test: GameAsserts<SkullGameModel>

    suspend fun setup(coroutineScope: CoroutineScope) {
        val setup = GameSetupImpl(dsl)
        val blocking = BlockingGameListener()
        game = setup.startGame(coroutineScope, 3) {
            listOf(blocking)
        }
        test = GameAsserts(game, blocking)
    }

    @Test
    fun startingPosition() = runTest {
        setup(this)
        test.expectPossibleActions(1, 0)
        test.expectPossibleActions(2, 0)

        val actions = test.expectPossibleActions(0, 4)
        Assertions.assertEquals(1, actions.count { it.parameter == SkullCard.SKULL })
        Assertions.assertEquals(3, actions.count { it.parameter == SkullCard.FLOWER })
        Assertions.assertTrue(game.model.players.all { it.points == 0 })
        println("4")

        test.performAction(0, "play", SkullCard.FLOWER)
        test.expectPossibleActions(2, 0)
        Assertions.assertEquals(1, game.model.currentPlayerIndex)
        test.performAction(1, "play", SkullCard.FLOWER)
        Assertions.assertEquals(2, game.model.currentPlayerIndex)
        test.performAction(2, "play", SkullCard.FLOWER)
        test.expectPossibleActions(0, 1 + 2 + 3) // Play 1 skull, play 2 flowers, 3 different bets
        test.performAction(0, "bet", 1)
        test.expectPossibleActions(1, 2 + 1) // bet 2, bet 3, pass
        test.performAction(1, "bet", 2)
        test.expectPossibleActions(2, 1 + 1) // bet 3, pass
        test.performAction(2, "pass", 1)
        test.expectPossibleActions(0, 1 + 1) // bet 3, pass
        test.performAction(0, "bet", 3)

        Assertions.assertEquals(0, game.model.currentPlayerIndex)
        test.expectPossibleActions(0, 1) // choose self
        test.performAction(0, "choose", game.model.players[0])
        Assertions.assertEquals(0, game.model.currentPlayerIndex)
        test.expectPossibleActions(0, 2) // choose another player
        test.performAction(0, "choose", game.model.players[2])
        test.expectPossibleActions(0, 1) // choose last player
        Assertions.assertTrue(game.model.players.all { it.points == 0 })
        test.performAction(0, "choose", game.model.players[1])
        println("1")

        Assertions.assertEquals(1, game.model.players[0].points)
        Assertions.assertEquals(0, game.model.currentPlayerIndex)


        test.performAction(0, "play", SkullCard.FLOWER)
        test.performAction(1, "play", SkullCard.SKULL)
        test.performAction(2, "play", SkullCard.SKULL)
        test.performAction(0, "bet", 3)
        test.performAction(0, "choose", game.model.players[0])
        println("2")
        test.performAction(0, "choose", game.model.players[1])
        Assertions.assertEquals(3, game.model.players[0].totalCards)
        // With the default options, currentPlayer should not change if you lose a bet.
        Assertions.assertEquals(0, game.model.currentPlayerIndex)
        game.stop()
        println("3")
    }

}