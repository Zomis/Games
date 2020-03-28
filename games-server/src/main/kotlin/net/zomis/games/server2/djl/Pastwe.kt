package net.zomis.games.server2.djl

import ai.djl.Model
import ai.djl.basicmodelzoo.basic.Mlp
import ai.djl.ndarray.NDArray
import ai.djl.ndarray.NDList
import ai.djl.ndarray.NDManager
import ai.djl.ndarray.types.Shape
import ai.djl.training.DefaultTrainingConfig
import ai.djl.training.dataset.ArrayDataset
import ai.djl.training.loss.Loss
import ai.djl.training.optimizer.Optimizer
import ai.djl.training.optimizer.learningrate.LearningRateTracker
import ai.djl.translate.Translator
import ai.djl.translate.TranslatorContext
import java.util.Scanner
import kotlin.random.Random

class DJLReinforcementMCVE2 {

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
            if (values[action]) return -1f
            values[action] = true
            return 1f
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
            return NDList(ctx.ndManager.create(input.map { if (it) 1f else 0f }.toFloatArray()))
        }

    }

    class HelloWorldGameTranslator : Translator<BooleanArray, Int> {
        private val random = Random(42)
        private val inputTranslator = HelloWorldNoTranslation()

        override fun processOutput(ctx: TranslatorContext, list: NDList): Int {
            val values = list[0].toFloatArray()
            println("Output ${values.joinToString()}")
            if (random.nextDouble() < 0.1) {
                val randomAction = random.nextInt(values.size)
                println("Choosing random action: $randomAction")
                return randomAction
            }
            return values.withIndex().maxBy { it.value.toDouble() }!!.index
        }

        override fun processInput(ctx: TranslatorContext, input: BooleanArray): NDList
            = inputTranslator.processInput(ctx, input)
    }

    class Agent {
        private val experiences = mutableListOf<HelloWorldExperience>()

        private val block = Mlp(4, 4, intArrayOf())
        private val model = Model.newInstance().also {
            it.block = block
        }
        private val predictor = model.newPredictor(HelloWorldGameTranslator())
        private val trainingConfig = DefaultTrainingConfig(Loss.l2Loss()).setBatchSize(2)
            .optOptimizer(Optimizer.adam().optLearningRateTracker(LearningRateTracker.fixedLearningRate(0.1f)).build())
        private val trainer = model.newTrainer(trainingConfig)
        init {
            trainer.initialize(Shape(1, 4))
        }

        fun decideMoveIndex(state: BooleanArray): Int = predictor.predict(state)
        fun saveExperience(state: BooleanArray, moveIndex: Int, reward: Float,
                       nextState: BooleanArray, gameOver: Boolean) {
            experiences.add(HelloWorldExperience(state, moveIndex, reward, nextState, gameOver))
        }
        fun train() {
            val batchSize = 5
            val batch = experiences.withIndex().shuffled().take(batchSize).also {
                list -> list.forEach { println(it) }
            }.map { it.value }
            experiences.clear()
            val trainTranslator = HelloWorldNoTranslation()
            val trainPredictor = model.newPredictor(trainTranslator)
            val qTarget = trainPredictor.batchPredict(batch.map { it.nextState })

            batch.withIndex().filter { it.value.gameOver }.forEach {
                // Terminal states don't really have a qValue so ignore them from next state
                model.ndManager.create(floatArrayOf(0f, 0f, 0f, 0f))
            }

            val data = model.ndManager.create(batch.map { it.state.toFloatArray() }.toTypedArray())
            val labels = model.ndManager.create(qTarget.withIndex().map {
                // What about the action taken?
                println("Label before modify ${it.index}: ${it.value.joinToString()}")
                val copy = it.value.copyOf()
                val batchValue = batch[it.index]
                copy[batchValue.action] = copy[batchValue.action] * 0.9f + batchValue.reward
                copy
//                it.value.map { f -> f * 0.9f + batch[it.index].reward }.toFloatArray()
            }.toTypedArray())
            println("Data: $data")
            println("Labels: $labels")

            val dataset = ArrayDataset.Builder()
                .setData(data)
                .optLabels(labels)
                .setSampling(batchSize, false)
                .build()
            // optimizer learning rate?

//            Batch(model.ndManager, data, labels)
            trainer.iterateDataset(dataset).forEach {
                trainer.trainBatch(it)
                trainer.step()
            }


//            _, cost = sess.run([qnn.optimizer, qnn.cost],
//            feed_dict={qnn.states: np.array(list(map(lambda x: x['state'], batch))),
//                qnn.r: np.array(list(map(lambda x: x['reward'], batch))),
//                qnn.enum_actions: np.array(list(enumerate(map(lambda x: x['action'], batch)))),
//                qnn.q_target: q_target})
        }
    }

    fun run() {
        val scanner = Scanner(System.`in`)
        val agent = Agent()
        var actionsMade = 0
        repeat(500) {gameNumber ->
            val game = HelloWorldGame(4)
            var totalReward = 0f
            val actions = mutableListOf<Int>()
            while (!game.isDone()) {
                val state = game.values.copyOf()
                print(state.joinToString() + "\t")
                val moveIndex = agent.decideMoveIndex(state)
                actions.add(moveIndex)
                val reward = game.performActionObserveReward(moveIndex)
                totalReward += reward
                val nextState = game.values.copyOf()
                agent.saveExperience(state, moveIndex, reward, nextState, game.isDone())
                if (++actionsMade % 10 == 0) {
                    println("$actionsMade actions made. Training time. Actions ${actions.joinToString("")}")
                    agent.train()
                    scanner.nextLine()
                }
            }
            println("Finished game $gameNumber with $totalReward. Actions $actions")
        }
    }

}

private fun BooleanArray.toNDArray(manager: NDManager): NDArray {
    return manager.create(this.toFloatArray())
}
private fun BooleanArray.toFloatArray(): FloatArray {
    return this.map { if (it) 1f else 0f }.toFloatArray()
}

fun main() {
    DJLReinforcementMCVE2().run()
}


/**
I'm trying to get a simple Reinforcement Learning working, but I ran into a problem: In order to do the first trainings, I need to play the game, but in order to play the game I need an initialized array - which seems to be initialized through training.

Exception in thread "main" java.lang.IllegalStateException: The array has not been initialized
at ai.djl.nn.Parameter.getArray(Parameter.java:125)
at ai.djl.training.ParameterStore.getValue(ParameterStore.java:110)
at ai.djl.nn.core.Linear.opInputs(Linear.java:175)
at ai.djl.nn.core.Linear.forward(Linear.java:74)

Example code to reproduce my problem: https://gist.github.com/Zomis/5dc8fa8a16e68d6ce5f10bd8ddb82751
The example game I'm trying to make an agent learn: https://github.com/shakedzy/notebooks/tree/master/q_learning_and_dqn

Can I initialize all weights to random somehow?

I tried running this:
*/