package net.zomis.games.server2.djl

import ai.djl.Model
import ai.djl.basicmodelzoo.basic.Mlp
import ai.djl.ndarray.NDArray
import ai.djl.ndarray.NDList
import ai.djl.ndarray.NDManager
import ai.djl.ndarray.types.Shape
import ai.djl.training.DefaultTrainingConfig
import ai.djl.training.dataset.ArrayDataset
import ai.djl.training.initializer.XavierInitializer
import ai.djl.training.loss.Loss
import ai.djl.training.optimizer.Optimizer
import ai.djl.training.optimizer.learningrate.LearningRateTracker
import ai.djl.translate.Translator
import ai.djl.translate.TranslatorContext
import net.zomis.bestBy
import net.zomis.games.dsl.Actionable
import net.zomis.games.dsl.impl.GameImpl
import net.zomis.games.dsl.impl.GameSetupImpl
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

    class LearningAgent(val actions: Int) {
        private val experiences = mutableListOf<LearningExperience>()

        private val block = Mlp(actions, actions, intArrayOf())
        private val model = Model.newInstance().also {
            it.block = block
        }
        private val predictor = model.newPredictor(NoTranslation())
        private val trainingConfig = DefaultTrainingConfig(Loss.l2Loss()).setBatchSize(2)
                .optInitializer(XavierInitializer())
                .optOptimizer(Optimizer.adam().optLearningRateTracker(
                    LearningRateTracker.fixedLearningRate(0.1f)
                ).build())
        private val trainer = model.newTrainer(trainingConfig)
        init {
            trainer.initialize(Shape(1, actions.toLong()))
        }

        fun runNetwork(state: FloatArray): FloatArray = predictor.predict(state)
        fun saveExperience(state: FloatArray, moveIndex: Int, reward: Float,
                   nextState: FloatArray, gameOver: Boolean) {
            experiences.add(LearningExperience(state, moveIndex, reward, nextState, gameOver))
        }
        fun train() {
            val batchSize = 5
            val batch = experiences.withIndex().shuffled().take(batchSize).also {
                list -> list.forEach { debug(it.toString()) }
            }.map { it.value }
            experiences.clear() // TODO: Use cyclic memory, don't remove all of it.
            val trainTranslator = NoTranslation()
            val trainPredictor = model.newPredictor(trainTranslator)
            val qTarget = trainPredictor.batchPredict(batch.map { it.nextState })

            batch.withIndex().filter { it.value.gameOver }.forEach {
                // Terminal states don't really have a qValue so ignore them from next state
                model.ndManager.create((0 until actions).map { 0f }.toFloatArray())
            }

            val data = model.ndManager.create(batch.map { it.state }.toTypedArray())
            val labels = model.ndManager.create(qTarget.withIndex().map {
                // What about the action taken?
                debug("Label before modify ${it.index}: ${it.value.joinToString()}")
                val copy = it.value.copyOf()
                val batchValue = batch[it.index]
                copy[batchValue.action] = batchValue.reward + 0.5f * copy[batchValue.action]
                copy
//                it.value.map { f -> f * 0.9f + batch[it.index].reward }.toFloatArray()
            }.toTypedArray())
            debug("Data: $data")
            debug("Labels: $labels")

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

        val dsl = HelloWorldGame.game
        val gameSetup = GameSetupImpl(dsl)
        val agent = LearningAgent(4)

        fun <T: Any> stateToFloats(state: GameImpl<T>): FloatArray {
            return when (val model = state.model) {
                is HelloWorldGame.HelloWorldModel -> model.values.map { if (it) 1f else 0f }.toFloatArray()
                else -> throw IllegalArgumentException("Unsupported type: ${state.model::class}")
            }
        }

        val random = Random.Default
        fun decideMoveIndex(floats: FloatArray): Int {
            if (random.nextDouble() < 0.1) {
                val randomAction = random.nextInt(floats.size)
//                println("Choosing random action: $randomAction")
                return randomAction
            }
            return floats.withIndex().maxBy { it.value.toDouble() }!!.index
        }

        repeat(300) {gameNumber ->
            val game = gameSetup.createGame(1, gameSetup.getDefaultConfig())
            var totalReward = 0f
            var actionsMade = 0
            val actions = mutableListOf<Int>()
            while (!game.isGameOver()) {
                debug("Current state: $game")
                val stateFloats = stateToFloats(game)
                val agentOutput = agent.runNetwork(stateFloats)
                val moveIndex = decideMoveIndex(agentOutput)
                actions.add(moveIndex)
                val reward = game.performActionObserveReward(moveIndex)
                totalReward += reward
                val nextState = stateToFloats(game)
                agent.saveExperience(stateFloats, moveIndex, reward, nextState, game.isGameOver())
                if (++actionsMade % 10 == 0) {
//                    println("$actionsMade actions made. Training time. Actions ${actions.joinToString("")}")
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

    private fun <T: Any> GameImpl<T>.performActionObserveReward(moveIndex: Int): Float {
        val playerIndex = 0
        val move = this.actions.types()
            .sortedBy { it.name }.flatMap { it.availableActions(playerIndex) }[moveIndex]
        this.actions.type(move.actionType)!!.perform(playerIndex, move.parameter)
        this.stateCheck()
        return this.eliminationCallback.eliminations().find { it.playerIndex == playerIndex }?.winResult?.result?.toFloat() ?: 0f
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
