package net.zomis.games.server2.djl

import ai.djl.Model
import ai.djl.basicmodelzoo.basic.Mlp
import ai.djl.ndarray.NDList
import ai.djl.ndarray.types.Shape
import ai.djl.training.DefaultTrainingConfig
import ai.djl.training.dataset.ArrayDataset
import ai.djl.training.initializer.XavierInitializer
import ai.djl.training.loss.Loss
import ai.djl.training.optimizer.Optimizer
import ai.djl.training.optimizer.learningrate.LearningRateTracker
import ai.djl.translate.Translator
import ai.djl.translate.TranslatorContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import net.zomis.games.dsl.impl.Game
import net.zomis.games.dsl.impl.GameSetupImpl
import net.zomis.games.dsl.listeners.BlockingGameListener
import java.util.Scanner
import kotlin.random.Random

class DJLReinforcement {

    class LearningExperience(
        val state: FloatArray,
        val action: Int,
        val reward: Float,
        val nextState: FloatArray,
        val gameOver: Boolean
    ) {
        override fun toString(): String = "${state.joinToString()} action $action. Reward $reward. Next: ${nextState.joinToString()} GameOver $gameOver"
    }

    class NoTranslation : Translator<FloatArray, FloatArray> {
        override fun processInput(ctx: TranslatorContext, input: FloatArray): NDList {
            return NDList(ctx.ndManager.create(input))
        }

        override fun processOutput(ctx: TranslatorContext, list: NDList): FloatArray {
            return list[0].toFloatArray()
        }
    }

    class LearningAgent(val actions: Int, val agentParameters: AgentParameters) {
        private val experiences = mutableListOf<LearningExperience>()
        private val inputs = 2*3*2

        private val block = Mlp(inputs, actions, intArrayOf(inputs / 2))
        private val model = Model.newInstance().also {
            it.block = block
        }
        private val predictor = model.newPredictor(NoTranslation())
        private val trainingConfig = DefaultTrainingConfig(Loss.l2Loss()).setBatchSize(2)
                .optInitializer(XavierInitializer())
                .optOptimizer(Optimizer.adam().optLearningRateTracker(
                    LearningRateTracker.fixedLearningRate(agentParameters.learningRate)
                ).build())
        private val trainer = model.newTrainer(trainingConfig)
        init {
            trainer.initialize(Shape(1, inputs.toLong()))
        }

        fun runNetwork(state: FloatArray): FloatArray {
            val result = predictor.predict(state)
            agentParameters.printConfig.output("Network result: ${result.joinToString()}")
            return result
        }
        fun saveExperience(state: FloatArray, moveIndex: Int, reward: Float,
                   nextState: FloatArray, gameOver: Boolean) {
            experiences.add(LearningExperience(state, moveIndex, reward, nextState, gameOver))
        }
        fun train() {
//            val batchSize = agentParameters.batchSize
            val batchSize = experiences.size
            val batch = experiences.withIndex().shuffled().take(batchSize).also {
                list -> list.forEach { agentParameters.printConfig.experience("Batch Part: $it") }
            }.map { it.value }
            experiences.clear() // TODO: Use cyclic memory, don't remove all of it.
            val trainTranslator = NoTranslation()
            val trainPredictor = model.newPredictor(trainTranslator)
            val qTarget = trainPredictor.batchPredict(batch.map { it.nextState })

            batch.withIndex().filter { it.value.gameOver }.forEach {
                // Terminal states don't really have a qValue (getting there has a reward) so ignore them from next state
                model.ndManager.create((0 until actions).map { 0f }.toFloatArray())
            }

            val data = model.ndManager.create(batch.map { it.state }.toTypedArray())
            val labels = model.ndManager.create(qTarget.withIndex().map {
                // What about the action taken?
                agentParameters.printConfig.training("Label before modify ${it.index}: ${it.value.joinToString()}")
                val copy = it.value.copyOf()
                val batchValue = batch[it.index]
                copy[batchValue.action] = batchValue.reward + agentParameters.discountFactor * copy[batchValue.action]
                copy
//                it.value.map { f -> f * 0.9f + batch[it.index].reward }.toFloatArray()
            }.toTypedArray())
            agentParameters.printConfig.training("Data: $data")
            agentParameters.printConfig.training("Labels: $labels")

            val dataset = ArrayDataset.Builder()
                .setData(data)
                .optLabels(labels)
                .setSampling(batchSize, false)
                .build()

            trainer.iterateDataset(dataset).forEach {
                trainer.trainBatch(it)
                trainer.step()
            }
        }
    }


    fun play() {
        val scanner = Scanner(System.`in`)
        val agentParameters = AgentParameters(
            learningRate = 0.2f,
            discountFactor = 0.99f,
            randomMoveProbability = 0.05,
            batchSize = 5,
            printConfig = PrintConfigs(
                state = false,
                experience = false,
                output = false,
                training = false,
                playing = false
            )
        )

//        val dsl = HelloWorldGame.game
        val dsl = GridWorldGame.game
        val gameSetup = GameSetupImpl(dsl)
        val agent = LearningAgent(4, agentParameters)

        val actionsMade = IntCounter(0)
        runBlocking {
            repeat(1000) {seriesNumber ->
                val seriesAwards = playSeries(this, 100, agent, gameSetup, actionsMade)
                println("Series $seriesNumber done. Total series awards: $seriesAwards")
                scanner.nextLine()
            }
        }
    }

    fun <T: Any> stateToFloats(state: Game<T>): FloatArray {
        return when (val model = state.model) {
            is HelloWorldGame.HelloWorldModel -> model.values.map { if (it) 1f else 0f }.toFloatArray()
            is GridWorldGame.GridWorldModel -> GridWorldGame.stateMapper(model)
            else -> throw IllegalArgumentException("Unsupported type: ${state.model::class}")
        }
    }

    val random = Random.Default
    fun decideMoveIndex(agentParameters: AgentParameters, floats: FloatArray): Int {
        if (random.nextDouble() < agentParameters.randomMoveProbability) {
            val randomAction = random.nextInt(floats.size)
//                println("Choosing random action: $randomAction")
            return randomAction
        }
        return floats.withIndex().maxByOrNull { it.value.toDouble() }!!.index
    }

    data class IntCounter(var value: Int)
    suspend fun <T: Any> playSeries(coroutineScope: CoroutineScope, count: Int, agent: LearningAgent, gameSetup: GameSetupImpl<T>, actionsMade: IntCounter): Float {
        var seriesAwards = 0f
        repeat(count) {gameNumber ->
            val blocking = BlockingGameListener()
            val game = gameSetup.startGame(coroutineScope, 1) {
                listOf(blocking)
            }
            var totalReward = 0f
            val actions = mutableListOf<Int>()
            blocking.await()
            while (!game.isGameOver()) {
                agent.agentParameters.printConfig.state("Current state: ${game.view(0)}")
                val stateFloats = stateToFloats(game)
                val agentOutput = agent.runNetwork(stateFloats)
                val moveIndex = decideMoveIndex(agent.agentParameters, agentOutput)
                actions.add(moveIndex)
                val reward = game.performActionObserveReward(blocking, moveIndex)
                totalReward += reward
                val nextState = stateToFloats(game)
                agent.saveExperience(stateFloats, moveIndex, reward, nextState, game.isGameOver())
                if (++actionsMade.value % 10 == 0) {
//                    println("$actionsMade actions made. Training time. Actions ${actions.joinToString("")}")
                    agent.train()
                }
            }
            seriesAwards += totalReward
            println("Game $gameNumber: $totalReward points. Actions $actions")
        }
        return seriesAwards
    }

    private suspend fun <T: Any> Game<T>.performActionObserveReward(blockingGameListener: BlockingGameListener, moveIndex: Int): Float {
        val playerIndex = 0
        blockingGameListener.await()
        val move = this.actions.types()
            .sortedBy { it.name }.flatMap { it.availableActions(playerIndex, null) }[moveIndex]
        blockingGameListener.awaitAndPerform(move)
        blockingGameListener.await()
        return this.eliminations.eliminations().find { it.playerIndex == playerIndex }?.winResult?.result?.toFloat()?.times(100) ?: -0.01f
    }

}

fun main() {
    DJLReinforcement().play()
}

// Environments: HelloWorld, Grid-World, TTT to start with.
// Later: Reversi, UTTT, Connect4, Quixo, TTT3D
// Trickier: Artax (up to 4 players)
// Trickier: UR (randomness, next player can vary)

// Train using a memory of 100 or so experiences
// Record state, action and next state(?)

// Network: Input state, output Q-values for all actions


// Choose action using random weighted (check min and max for negativity, use diffs?) Use sigmoid?
