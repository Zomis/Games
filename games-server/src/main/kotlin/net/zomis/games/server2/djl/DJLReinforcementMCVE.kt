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
import java.util.Scanner
import kotlin.random.Random

class DJLReinforcementMCVE {

    class HelloWorldExperience(
        val state: BooleanArray,
        val action: Int,
        val reward: Float,
        val nextState: BooleanArray,
        val gameOver: Boolean
    ) {
        override fun toString(): String = "${state.joinToString()} action $action. Reward $reward. Next: ${nextState.joinToString()} GameOver $gameOver"
    }
    class HelloWorldGame(val size: Int) {
        val values: BooleanArray = (0 until size).map { false }.toBooleanArray()

        fun performActionObserveReward(action: Int): Float {
            val power = 100f
            if (values[action]) return -1 * power
            values[action] = true
            return 1 * power
        }

        fun isDone(): Boolean {
            return values.all { it }
        }
    }

    class HelloWorldNoTranslation : Translator<BooleanArray, FloatArray> {
        override fun processOutput(ctx: TranslatorContext, list: NDList): FloatArray {
            return list[0].toFloatArray()
        }

        override fun processInput(ctx: TranslatorContext, input: BooleanArray): NDList {
            return NDList(ctx.ndManager.create(input.toFloatArray()))
        }
    }

    class HelloWorldGameTranslator(private val agentParameters: AgentParameters) : Translator<BooleanArray, Int> {
        private val random = Random(42)
        private val inputTranslator = HelloWorldNoTranslation()

        override fun processOutput(ctx: TranslatorContext, list: NDList): Int {
            val values = list[0].toFloatArray()
            agentParameters.printConfig.output("Output ${values.joinToString()}")
            if (random.nextDouble() < agentParameters.randomMoveProbability) {
                val randomAction = random.nextInt(values.size)
                agentParameters.printConfig.playing("Choosing random action: $randomAction")
                return randomAction
            }
            return values.withIndex().maxBy { it.value.toDouble() }!!.index
        }

        override fun processInput(ctx: TranslatorContext, input: BooleanArray): NDList
            = inputTranslator.processInput(ctx, input)
    }

    class Agent(val actions: Int, private val agentParameters: AgentParameters) {
        private val experiences = mutableListOf<HelloWorldExperience>()

        private val block = Mlp(actions, actions, intArrayOf())
        private val model = Model.newInstance().also {
            it.block = block
        }
        private val predictor = model.newPredictor(HelloWorldGameTranslator(agentParameters))
        private val trainingConfig = DefaultTrainingConfig(Loss.l2Loss()).setBatchSize(2)
                .optInitializer(XavierInitializer())
                .optOptimizer(Optimizer.adam().optLearningRateTracker(
                    LearningRateTracker.fixedLearningRate(agentParameters.learningRate)
                ).build())
        private val trainer = model.newTrainer(trainingConfig)
        init {
            trainer.initialize(Shape(1, actions.toLong()))
        }

        fun decideMoveIndex(state: BooleanArray): Int = predictor.predict(state)
        fun saveExperience(state: BooleanArray, moveIndex: Int, reward: Float,
                       nextState: BooleanArray, gameOver: Boolean) {
            experiences.add(HelloWorldExperience(state, moveIndex, reward, nextState, gameOver))
        }
        fun train() {
            val batchSize = agentParameters.batchSize
            val batch = experiences.withIndex().shuffled().take(batchSize).also {
                list -> list.forEach { agentParameters.printConfig.experience(it.toString()) }
            }.map { it.value }
            experiences.clear() // TODO: Use cyclic memory, don't remove all of it.
            val trainTranslator = HelloWorldNoTranslation()
            val trainPredictor = model.newPredictor(trainTranslator)
            val qTarget = trainPredictor.batchPredict(batch.map { it.nextState })

            batch.withIndex().filter { it.value.gameOver }.forEach {
                // Terminal states don't really have a qValue so ignore them from next state
                model.ndManager.create(it.value.state.map { 0f }.toFloatArray())
            }

            val data = model.ndManager.create(batch.map { it.state.toFloatArray() }.toTypedArray())
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

    fun run() {
        val scanner = Scanner(System.`in`)
        val actionsCount = 4
        val agentParameters = AgentParameters(
            learningRate = 0.1f,
            discountFactor = 0f,
            randomMoveProbability = 0.1,
            batchSize = 5,
            printConfig = PrintConfigs(
                state = false,
                experience = false,
                output = false,
                training = false,
                playing = false
            )
        )
        val agent = Agent(actionsCount, agentParameters)
        var actionsMade = 0
        repeat(1000) {gameNumber ->
            val game = HelloWorldGame(actionsCount)
            var totalReward = 0f
            val actions = mutableListOf<Int>()
            while (!game.isDone()) {
                val state = game.values.copyOf()
                agentParameters.printConfig.state("Current state: " + state.joinToString())
                val moveIndex = agent.decideMoveIndex(state)
                actions.add(moveIndex)
                val reward = game.performActionObserveReward(moveIndex)
                totalReward += reward
                val nextState = game.values.copyOf()
                agent.saveExperience(state, moveIndex, reward, nextState, game.isDone())
                if (++actionsMade % 10 == 0) {
                    agentParameters.printConfig.training("$actionsMade actions made. Training time. Actions ${actions.joinToString("")}")
                    agent.train()
                }
            }
//            println("Finished game $gameNumber with $totalReward. Actions $actions")
            println("Game $gameNumber: $totalReward points")
            if (gameNumber % 5 == 4) {
//                scanner.nextLine()
            }
        }
    }
}

private fun BooleanArray.toFloatArray(): FloatArray {
    return this.map { if (it) 1f else 0f }.toFloatArray()
}

fun main() {
    DJLReinforcementMCVE().run()
}
