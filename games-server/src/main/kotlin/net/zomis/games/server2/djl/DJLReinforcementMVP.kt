package net.zomis.games.server2.djl

import ai.djl.Model
import ai.djl.basicmodelzoo.basic.Mlp
import ai.djl.ndarray.NDList
import ai.djl.ndarray.NDManager
import ai.djl.training.DefaultTrainingConfig
import ai.djl.training.loss.Loss
import ai.djl.translate.Translator
import ai.djl.translate.TranslatorContext
import kotlin.random.Random

class DJLReinforcementMCVE {

//    class Experience(val state: BooleanArray, val action: Int, val reward: Double)

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

    class HelloWorldGameTranslator : Translator<BooleanArray, Int> {
        private val random = Random(42)

        override fun processOutput(ctx: TranslatorContext, list: NDList): Int {
            val values = list[0].toFloatArray()
            if (random.nextDouble() < 0.1) {
                return random.nextInt(values.size)
            }
            return values.withIndex().maxBy { it.value.toDouble() }!!.index
        }

        override fun processInput(ctx: TranslatorContext, input: BooleanArray): NDList {
            return NDList(ctx.ndManager.create(input.map { if (it) 1f else 0f }.toFloatArray()))
        }
    }

    class Agent {

        private val block = Mlp(4, 4, intArrayOf(4))
        private val model = Model.newInstance().also {
//            block.setInitializer(XavierInitializer())
            it.block = block
        }
        private val manager = NDManager.newBaseManager()
        private val predictor = model.newPredictor(HelloWorldGameTranslator())

        init {
            val trainingConfig = DefaultTrainingConfig(Loss.l2Loss())
//                .addEvaluator(Accuracy())
                    .setBatchSize(2)
            val inputShape = ai.djl.ndarray.types.Shape(1, 4)
            model.newTrainer(trainingConfig).use { it.initialize(inputShape) }
        }

        fun decideMoveIndex(state: BooleanArray): Int = predictor.predict(state)

    }

    fun run() {
        val game = HelloWorldGame(4)
        val agent = Agent()
        while (!game.isDone()) {
            val state = game.values.copyOf()
            val moveIndex = agent.decideMoveIndex(state)
            val reward = game.performActionObserveReward(moveIndex)
            val nextState = game.values.copyOf()
//            agent.saveExperience(translatedView, moveIndex, reward)
        }

//        agent.train(3)
//        println(game.model.points)
    }

}

fun main() {
    DJLReinforcementMCVE().run()
}
