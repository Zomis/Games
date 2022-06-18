package net.zomis.games.dsl.flow

import kotlinx.coroutines.runBlocking
import net.zomis.games.dsl.GamesImpl
import net.zomis.games.dsl.impl.FlowStep
import net.zomis.games.impl.DslUR
import net.zomis.games.ur.RoyalGameOfUr
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class RoyalGameOfUrFlowTest {

    @Test
    fun test() {
        val entryPoint = GamesImpl.game(DslUR.gameUR)
        val config = entryPoint.setup().configs().also { it.set("piecesPerPlayer", 1) }
        val gameFlowImpl = entryPoint.setup().createGame(2, config) as GameFlowImpl<RoyalGameOfUr>
        val test = GameFlowTestHelper(gameFlowImpl)
        val model = gameFlowImpl.model
        runBlocking {
            Assertions.assertEquals(1, model.piecesCopy[0].count { it == 0 })
            test.takeUntil { it is FlowStep.AwaitInput }

            val view = gameFlowImpl.view(0)
            println(view)
            Assertions.assertEquals(0, view["currentPlayer"] as Int)

            while (!gameFlowImpl.isGameOver()) {
                val currentPlayer = model.currentPlayer
                gameFlowImpl.actionsInput.send(gameFlowImpl.actions.type(DslUR.roll)!!.createAction(model.currentPlayer, Unit))
                test.takeUntil { it is FlowStep.AwaitInput }
                println(gameFlowImpl.view(0))
                Assertions.assertTrue(model.roll > 0 || model.currentPlayer == 1 - currentPlayer)
                if (model.roll > 0) {
                    gameFlowImpl.actionsInput.send(gameFlowImpl.actions.type(DslUR.move)!!
                        .createAction(model.currentPlayer, model.piecesCopy[model.currentPlayer][0]))
                    println("output...")
                    test.takeUntil { it is FlowStep.ActionPerformed<*> }
                    var output = gameFlowImpl.feedbackReceiver.receive()
                    println("output4 $output")
                    if (output is FlowStep.GameEnd) {
                        break
                    }
                    if (output is FlowStep.RuleExecution && output.ruleName != "view") {
                        output = gameFlowImpl.feedbackReceiver.receive()
                        println("output5 $output")
                        Assertions.assertTrue(output is FlowStep.Elimination)
                        output = gameFlowImpl.feedbackReceiver.receive()
                        println("output6 $output")
                        Assertions.assertTrue(output is FlowStep.Elimination)
                        output = gameFlowImpl.feedbackReceiver.receive()
                        println("output7 $output")
                        Assertions.assertTrue(output is FlowStep.GameEnd)
                    } else if (output !is FlowStep.AwaitInput) {
                        test.takeUntil { it is FlowStep.AwaitInput }
                    }
                }
            }
            println("game is over")
            Assertions.assertTrue(gameFlowImpl.isGameOver())
        }
    }

}