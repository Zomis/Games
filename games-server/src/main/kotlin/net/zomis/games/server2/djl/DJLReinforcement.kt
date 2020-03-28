package net.zomis.games.server2.djl

import ai.djl.Model
import ai.djl.basicmodelzoo.basic.Mlp
import ai.djl.ndarray.NDArray
import ai.djl.ndarray.NDList
import ai.djl.ndarray.NDManager
import ai.djl.training.DefaultTrainingConfig
import ai.djl.training.initializer.XavierInitializer
import ai.djl.training.loss.Loss
import ai.djl.translate.Translator
import ai.djl.translate.TranslatorContext
import net.zomis.bestBy
import net.zomis.games.dsl.Actionable
import net.zomis.games.dsl.impl.GameImpl
import net.zomis.games.dsl.impl.GameSetupImpl
import kotlin.random.Random

class DJLReinforcement {

    data class TranslatedView(val floats: NDArray)
    data class Experience(val view: TranslatedView, val action: Int, val reward: Double)

    class MyTranslator(private val random: Random) : Translator<TranslatedView, Int> {
        override fun processInput(ctx: TranslatorContext, input: TranslatedView): NDList {
            return NDList(input.floats)
        }

        override fun processOutput(ctx: TranslatorContext, list: NDList): Int {
            val values = list[0].toFloatArray()
            if (random.nextDouble() < 0.1) {
                return random.nextInt(values.size)
            }
            return values.withIndex().bestBy { it.value.toDouble() }.random().index
        }
    }

    class Agent(random: Random) {
        private val block = Mlp(4, 4, intArrayOf(4))
        private val model = Model.newInstance().also {
            block.setInitializer(XavierInitializer())
            it.block = block
        }
        private val manager = NDManager.newBaseManager()
        private val predictor = model.newPredictor(MyTranslator(random))

        init {
            val trainingConfig = DefaultTrainingConfig(Loss.l2Loss())
//                .addEvaluator(Accuracy())
                .setBatchSize(2)
//            val inputShape = ai.djl.ndarray.types.Shape(1, 4).

//            model.newTrainer(trainingConfig).use { it.initialize(inputShape) }
        }

        fun understandView(view: Map<String, Any?>): TranslatedView {
            val v = view["board"] as List<Boolean>
            return TranslatedView(manager.create(v.map { if (it) 1 else 0 }.toIntArray()))
        }

        fun decideMoveIndex(translatedView: TranslatedView): Int {
            return predictor.predict(translatedView)
        }

        fun observeReward(view: Map<String, Any?>, view2: Map<String, Any?>): Double {
            val scores = view["score"] as List<Int>
            val scores2 = view2["score"] as List<Int>
            return (scores2[0] - scores[0]).toDouble()
        }

        fun saveExperience(translatedView: TranslatedView, move: Int, reward: Double) {
            Experience(translatedView, move, reward)
        }

        fun train(trainingData: Int) {
            val trainingData = listOf(0).shuffled().take(3)
        }

    }

    fun play() {
        // Environments: HelloWorld, Grid-World, TTT to start with.
        // Later: Reversi, UTTT, Connect4, Quixo, TTT3D
        // Trickier: Artax (up to 4 players)
        // Trickier: UR (randomness, next player can vary)

        // Train using a memory of 100 or so experiences
        // Record state, action and next state(?)

        // Network: Input state, output Q-values for all actions


        // Choose action using random weighted (check min and max for negativity, use diffs?) Use sigmoid?

        val dsl = HelloWorldGame.game
        val gameSetup = GameSetupImpl(dsl)
        val game = gameSetup.createGame(1, gameSetup.getDefaultConfig())
        val agent = Agent(Random.Default)
        while (!game.isGameOver()) {
            val state = game.view(0)
            val translatedView = agent.understandView(state)
            val moveIndex = agent.decideMoveIndex(translatedView)
            val move = game.actions.types()
                    .sortedBy { it.name }.flatMap { it.availableActions(0) }[moveIndex]
            performMove(game, move)
            val nextState = game.view(0)
            val reward = agent.observeReward(state, nextState)
            agent.saveExperience(translatedView, moveIndex, reward)
        }

        agent.train(3)
        println(game.model.points)

    }

    private fun <T: Any> performMove(game: GameImpl<T>, move: Actionable<T, Any>) {
        game.actions.type(move.actionType)!!.perform(0, move.parameter)
    }

}

fun main() {
    DJLReinforcement().play()
}