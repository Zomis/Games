package net.zomis.games.dsl.flow

import klog.KLoggers
import kotlinx.coroutines.test.runTest
import net.zomis.games.dsl.GamesImpl
import net.zomis.games.dsl.impl.FlowStep
import net.zomis.games.impl.DslUR
import net.zomis.games.listeners.SteppingGameListener
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class RoyalGameOfUrFlowTest {
    val logger = KLoggers.logger(this)

    @Test
    fun test() = runTest {
        val entryPoint = GamesImpl.game(DslUR.gameUR)
        val config = entryPoint.setup().configs().also { it.set("piecesPerPlayer", 1) }
        val test = SteppingGameListener()
        val gameFlowImpl = entryPoint.setup().startGameWithConfig(this, 2, config) {
            listOf(test)
        }
        val model = gameFlowImpl.model
        if (true) {
            Assertions.assertEquals(1, model.piecesCopy[0].count { it == 0 })
            test.takeUntil { it is FlowStep.AwaitInput }

            val view = gameFlowImpl.view(0)
            logger.info(view)
            Assertions.assertEquals(0, view["currentPlayer"] as Int)

            while (!gameFlowImpl.isGameOver()) {
                val currentPlayer = model.currentPlayer
                logger.info("sending roll")
                gameFlowImpl.actionsInput.send(gameFlowImpl.actions.type(DslUR.roll)!!.createAction(model.currentPlayer, Unit))
                logger.info("roll sent, awaiting AwaitInput")
                test.takeUntil { it is FlowStep.AwaitInput }
                logger.info("awaited input after roll")
                logger.info("View: " + gameFlowImpl.view(0))
                Assertions.assertTrue(model.roll > 0 || model.currentPlayer == 1 - currentPlayer)
                if (model.roll > 0) {
                    gameFlowImpl.actionsInput.send(gameFlowImpl.actions.type(DslUR.move)!!
                        .createAction(model.currentPlayer, model.piecesCopy[model.currentPlayer][0]))
                    logger.info("output...")
                    test.takeUntil { it is FlowStep.ActionPerformed<*> }
                    var output = test.next()
                    logger.info("output4 $output")
                    if (output is FlowStep.GameEnd) {
                        break
                    }
                    if (output is FlowStep.RuleExecution && output.ruleName != "view") {
                        logger.info("rule: ${output.ruleName}")
                        output = test.next()
                        logger.info("output5 $output")
                        Assertions.assertTrue(output is FlowStep.Elimination)
                        output = test.next()
                        logger.info("output6 $output")
                        Assertions.assertTrue(output is FlowStep.Elimination)
                        output = test.next()
                        logger.info("output7 $output")
                        Assertions.assertTrue(output is FlowStep.GameEnd)
                    } else if (output !is FlowStep.AwaitInput) {
                        logger.info("outputNotAwait: $output")
                        test.takeUntil { it is FlowStep.AwaitInput }
                    } else {
                        logger.info("outputElse: $output")
                    }
                }
            }
            logger.info("game is over")
            Assertions.assertTrue(gameFlowImpl.isGameOver())
        }
    }

}