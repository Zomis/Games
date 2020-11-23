package net.zomis.games.dsl.flow

import kotlinx.coroutines.runBlocking
import net.zomis.games.dsl.GamesImpl
import net.zomis.games.impl.DslUR
import net.zomis.games.ur.RoyalGameOfUr
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class RoyalGameOfUrFlowTest {

    @Test
    fun test() {
        val entryPoint = GamesImpl.game(DslUR.gameUR)
        val gameFlowImpl = entryPoint.setup().createGame(2, DslUR.Config(1)) as GameFlowImpl<RoyalGameOfUr>
        val model = gameFlowImpl.model
        runBlocking {
            Assertions.assertEquals(1, model.piecesCopy[0].count { it == 0 })

            var output = gameFlowImpl.feedbackOutput.receive()
            Assertions.assertTrue(output is GameFlowContext.Steps.AwaitInput)

            val view = gameFlowImpl.view(0)
            println(view)
            Assertions.assertEquals(0, view["currentPlayer"] as Int)

            while (!gameFlowImpl.isGameOver()) {
                val currentPlayer = model.currentPlayer
                gameFlowImpl.actionsInput.send(gameFlowImpl.actions.type(DslUR.roll)!!.createAction(0, Unit))
                println("awaiting output")
                output = gameFlowImpl.feedbackOutput.receive()
                println("output1 $output")
                Assertions.assertTrue(output is GameFlowContext.Steps.ActionPerformed)
                output = gameFlowImpl.feedbackOutput.receive()
                println("output2 $output")
                Assertions.assertTrue(output is GameFlowContext.Steps.AwaitInput)
                println(gameFlowImpl.view(0))
//                println(gameFlowImpl.feedbackOutput.receive())
                Assertions.assertTrue(gameFlowImpl.model.roll > 0 || gameFlowImpl.model.currentPlayer == 1 - currentPlayer)
                if (model.roll > 0) {
                    gameFlowImpl.actionsInput.send(gameFlowImpl.actions.type(DslUR.move)!!
                        .createAction(0, model.piecesCopy[model.currentPlayer][0]))
                    println("output...")
                    output = gameFlowImpl.feedbackOutput.receive()
                    Assertions.assertTrue(output is GameFlowContext.Steps.ActionPerformed)
                    println("output3 $output")
                    output = gameFlowImpl.feedbackOutput.receive()
                    println("output4 $output")
                    if (output is GameFlowContext.Steps.RuleExecution) {
                        output = gameFlowImpl.feedbackOutput.receive()
                        Assertions.assertTrue(output is GameFlowContext.Steps.Elimination)
                        println("output5 $output")
                        output = gameFlowImpl.feedbackOutput.receive()
                        Assertions.assertTrue(output is GameFlowContext.Steps.Elimination)
                        println("output6 $output")
                        output = gameFlowImpl.feedbackOutput.receive()
                        Assertions.assertTrue(output is GameFlowContext.Steps.GameEnd)
                        println("output7 $output")
                    } else {
                        Assertions.assertTrue(output is GameFlowContext.Steps.AwaitInput)
                    }
                }
            }
            println("game is over")
        }
    }

}