package net.zomis.games.server2.djl

import ai.djl.Model
import ai.djl.modality.rl.LruReplayBuffer
import ai.djl.modality.rl.agent.EpsilonGreedy
import ai.djl.modality.rl.agent.QAgent
import ai.djl.modality.rl.agent.RlAgent
import ai.djl.modality.rl.env.RlEnv
import ai.djl.ndarray.NDManager
import ai.djl.ndarray.types.Shape
import ai.djl.nn.Block
import ai.djl.training.DefaultTrainingConfig
import ai.djl.training.TrainingConfig
import ai.djl.training.TrainingResult
import ai.djl.training.listener.TrainingListener
import ai.djl.training.loss.Loss
import ai.djl.training.optimizer.Adam
import ai.djl.training.tracker.LinearTracker
import ai.djl.training.tracker.Tracker
import klog.KLoggers
import kotlinx.coroutines.runBlocking
import net.zomis.games.dsl.Actionable
import net.zomis.games.dsl.ConsoleController
import net.zomis.games.dsl.impl.GameController
import net.zomis.games.dsl.impl.GameControllerScope
import net.zomis.games.server2.djl.impls.GridWorldHandler
import net.zomis.games.server2.djl.impls.HelloWorldHandler
import net.zomis.games.server2.djl.impls.TTTHandler
import java.util.Scanner
import java.util.function.Consumer

interface DJLFactory<T: Any, S> {
    fun shapes(config: Any, batchSize: Int): Array<Shape>
    fun handler(config: Any): DJLHandler<T, S>
    fun createBlock(): Block
}
class DJL {

    private val logger = KLoggers.logger(this)

    fun train(factory: DJLFactory<out Any, out Any>): TrainingResult {
        val rewardDiscount = 0.9f
        val epochs = 20
        val gamesPerEpoch = 128
        val batchSize = 32
        val validationGamesPerEpoch = 1
        check(batchSize > 0)
        val gameConfig = Unit

        val game = DJLGame(factory.handler(gameConfig), NDManager.newBaseManager(), LruReplayBuffer(batchSize, 1024))
        val block: Block = factory.createBlock()

        Model.newInstance("tic-tac-toe").use { model ->
            model.block = block

            val config = setupTrainingConfig()
            model.newTrainer(config).use { trainer ->
                trainer.initialize(*factory.shapes(gameConfig, batchSize))
                trainer.notifyListeners { listener: TrainingListener -> listener.onTrainingBegin(trainer) }

                // Constructs the agent to train and play with
                var agent: RlAgent = QAgent(trainer, rewardDiscount)
                val exploreRate: Tracker = LinearTracker.Builder()
                        .setBaseValue(0.9f)
                        .optSlope(-.9f / (epochs * gamesPerEpoch * 7))
                        .optMinValue(0.01f)
                        .build()
                agent = EpsilonGreedy(agent, exploreRate)
                var validationWinRate = 0f
                val trainWinRate = 0f
                logger.info { "Running $epochs epochs with $gamesPerEpoch games per epoch" }
                for (epoch in 1..epochs) {
                    var trainingWins = 0
                    var trainingOtherWins = 0
                    var trainingDraws = 0
                    var total = 0f
                    for (j in 0 until gamesPerEpoch) {
                        val result: Float = game.runEnvironment(agent, true)
                        val batchSteps: Array<RlEnv.Step> = game.batch
                        agent.trainBatch(batchSteps)
                        trainer.step()

                        // Record if the game was won
                        if (result > 0) {
                            trainingWins++
                        }
                        if (result < 0) {
                            trainingOtherWins++
                        }
                        if (result == 0f) {
                            trainingDraws++
                        }
                        total += result
                    }
                    val gamesCount = gamesPerEpoch.toFloat()
                    logger.info { "Training epoch $epoch/$epochs: ${trainingWins / gamesCount} / ${trainingDraws / gamesCount} / ${trainingOtherWins / gamesCount}. Total $total" }
                    trainer.notifyListeners(Consumer { listener: TrainingListener -> listener.onEpoch(trainer) })

                    // Counts win rate after playing {validationGamesPerEpoch} games
                    var validationWins = 0
                    for (j in 0 until validationGamesPerEpoch) {
                        val result: Float = game.runEnvironment(agent, false)
                        if (result > 0) {
                            validationWins++
                        }
                    }
                    validationWinRate = validationWins.toFloat() / validationGamesPerEpoch
                    logger.info { "Validation wins: $validationWinRate" }
                }
                trainer.notifyListeners(Consumer { listener: TrainingListener -> listener.onTrainingEnd(trainer) })
                val trainingResult: TrainingResult = trainer.trainingResult
                trainingResult.evaluations["validate_winRate"] = validationWinRate
                trainingResult.evaluations["train_winRate"] = trainWinRate

//                model.save(File("ttt-reinforcement.djl").toPath(), "tic-tac-toe")
                playGame(game, agent)
                return trainingResult
            }
        }

    }

    private class DJLController<T: Any>(val game: DJLGame<T, *>, val agent: RlAgent): GameController<T> {
        private var totalReward = 0f

        override fun invoke(context: GameControllerScope<T>): Actionable<T, Any>? {
            game.updateState()
            val action = agent.chooseAction(game, false)
//            val step: RlEnv.Step = game.step(action, false) // TODO: game.step actually *performs* the action, separate this.
//            totalReward += step.reward.getFloat()
            val actionable = game.handler.moveToAction(game.getGame(), action.singletonOrThrow().getInt())
            if (actionable.playerIndex != context.playerIndex) return null
            println("Agent is making action $actionable and has now $totalReward")
            return actionable as Actionable<T, Any>
        }
    }

    private fun <T: Any, S: Any> playGame(game: DJLGame<T, S>, agent: RlAgent) {
        val learningController = DJLController(game, agent)
        val scanner = Scanner(System.`in`)
        val humanController = ConsoleController<T>().humanController(scanner)
        runBlocking {
            game.reset()
            game.replayable.playThroughWithControllers {
                if (it == 0) humanController
                else learningController
            }
        }
        scanner.close()
    }

    private fun setupTrainingConfig(): TrainingConfig {
        return DefaultTrainingConfig(Loss.l2Loss())
                .addTrainingListeners(*TrainingListener.Defaults.basic())
                .optOptimizer(Adam.builder().optLearningRateTracker(Tracker.fixed(0.0001f)).build())
    }

}

fun main() {
    DJL().train(GridWorldHandler)
}
