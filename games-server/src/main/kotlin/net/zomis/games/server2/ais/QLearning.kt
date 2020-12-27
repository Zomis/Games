package net.zomis.games.server2.ais

import klog.KLoggers
import kotlin.random.Random


interface QStore<S> {
    fun getOrDefault(key: S, defaultValue: Double): Double
    fun put(key: S, value: Double)
    fun size(): Long
}

class QStoreMap<S>: QStore<S> {
    private val map: MutableMap<S, Double> = mutableMapOf()

    override fun getOrDefault(key: S, defaultValue: Double): Double {
        return map.getOrDefault(key, defaultValue)
    }

    override fun put(key: S, value: Double) {
        map[key] = value
    }

    override fun size(): Long {
        return map.size.toLong()
    }
}

typealias ActionPossible<T> = (environment: T, action: Int) -> Boolean
typealias PerformAction<T> = (environment: T, action: Int) -> MyQLearning.Rewarded<T>

data class QAwaitingReward<S>(val state: S, val stateAction: S, val action: Int)

class MyQLearning<T, S>(val maxActions: Int,
                        private val stateFunction: (T) -> S,
                        private val actionPossible: ActionPossible<T>,
                        private val stateActionFunction: (T, S, Int) -> S,
                        private val qTable: QStore<S>) {

    private val logger = KLoggers.logger(this)

    private val DEFAULT_QVALUE = 0.0
    private val EPSILON = 0.0001

    var discountFactor = 0.99
    var learningRate = 0.01
    var isEnabled = true
    var randomMoveProbability = 0.0
    private val random = Random.Default

    val qTableSize: Long
        get() = this.qTable.size()

    class Rewarded<T>(val state: T, val reward: Double) {
        var discountFactor: Double? = null

        fun withDiscountFactor(discountFactor: Double): Rewarded<T> {
            this.discountFactor = discountFactor
            return this
        }
    }


    fun step(environment: T, performAction: PerformAction<T>): Rewarded<T> {
        val action = pickAction(environment)
        return this.step(environment, performAction, action)
    }

    fun pickAction(environment: T): Int {
        return if (random.nextDouble() < randomMoveProbability) {
            pickRandomAction(environment)
        } else {
            pickBestAction(environment)
        }
    }

    fun pickRandomAction(environment: T): Int {
        val possibleActions = getPossibleActions(environment)
        val actionIndex = random.nextInt(possibleActions.size)
        return possibleActions[actionIndex]
    }

    fun prepareReward(environment: T, action: Int): QAwaitingReward<S> {
        val state = stateFunction(environment)
        val stateAction = stateActionFunction(environment, state, action)
        return QAwaitingReward(state, stateAction, action)
    }

    fun step(environment: T, performAction: PerformAction<T>, action: Int): Rewarded<T> {
        if (!isEnabled) {
            return performAction(environment, action)
        }
        val awaitReward = prepareReward(environment, action)

        val rewardedState = performAction(environment, action)
        return performReward(awaitReward, rewardedState)
    }

    fun performReward(awaitReward: QAwaitingReward<S>, rewardedState: Rewarded<T>): Rewarded<T> {
        if (rewardedState.discountFactor != null) {
            this.discountFactor = rewardedState.discountFactor!!
        }

        val nextState = rewardedState.state
        val rewardT = rewardedState.reward

        val nextStateStr = stateFunction(nextState)
        val estimateOfOptimalFutureValue = (0 until maxActions)
                .filter { i -> actionPossible(nextState, i) }
                .map { i -> stateActionFunction(rewardedState.state, nextStateStr, i) }
                .map { str -> qTable.getOrDefault(str, DEFAULT_QVALUE) }.max() ?: 0.0

        val oldValue = qTable.getOrDefault(awaitReward.stateAction, DEFAULT_QVALUE)
        val learnedValue = rewardT + discountFactor * estimateOfOptimalFutureValue
        val newValue = (1 - learningRate) * oldValue + learningRate * learnedValue
        logger.info { "$this Performed ${awaitReward.action} in state ${awaitReward.state} with reward $rewardT. Old Value $oldValue. Learned $learnedValue. New $newValue" }
        this.qTable.put(awaitReward.stateAction, newValue)
        return rewardedState
    }

    fun getActionScores(environment: T): DoubleArray {
        val state = stateFunction(environment)
        val result = DoubleArray(maxActions)
        for (i in 0 until maxActions) {
            if (actionPossible(environment, i)) {
                val st = stateActionFunction(environment, state, i)
                val value = qTable.getOrDefault(st, 0.0)
                result[i] = value
            }
        }
        return result
    }

    /**
     * Calculates the difference between each action score and the lowest score, then picks an action weighted randomly
     * @param environment Environment to pick an action in
     * @param bonus bonus to add to all actions, for more randomness. Negative value will lead to a preference towards the first action
     * @return Weighted random action based on score
     */
    fun pickWeightedBestAction(environment: T, bonus: Double = 0.0): Int {
        val state = stateFunction(environment)
        val possibleActions = getPossibleActions(environment)
        if (possibleActions.isEmpty()) {
            throw IllegalStateException("No successful action in $environment: $state")
        }
        val scores = DoubleArray(possibleActions.size)
        for (i in possibleActions.indices) {
            val action = possibleActions[i]
            val stateAction = stateActionFunction(environment, state, action)
            scores[i] = this.qTable.getOrDefault(stateAction, DEFAULT_QVALUE)
        }
        val min = scores.min() ?: 0.0
        var sum = 0.0
        for (i in scores.indices) {
            scores[i] = scores[i] - min + bonus
            sum += scores[i]
        }

        if (sum == 0.0) {
            val randomIndex = random.nextInt(possibleActions.size)
            return possibleActions[randomIndex]
        }
        var limit = random.nextDouble() * sum
        for (i in possibleActions.indices) {
            limit -= scores[i]
            if (limit < 0) {
                return possibleActions[i]
            }
        }
        throw IllegalStateException("No successful action because of some logic problem.")
    }

    fun pickBestAction(environment: T): Int {
        val state = stateFunction(environment)
        var numBestActions = 0
        var bestValue = -1000.0
        val possibleActions = getPossibleActions(environment)
        if (possibleActions.isEmpty()) {
            throw IllegalStateException("No successful action in $environment: $state")
        }
        if (possibleActions.size == 1) {
            // Only one possible thing to do, no need to perform additional analysis here
            return possibleActions[0]
        }
        for (i in possibleActions) {
            val stateAction = stateActionFunction(environment, state, i)
            val value = qTable.getOrDefault(stateAction, DEFAULT_QVALUE)
            val diff = Math.abs(value - bestValue)
            val better = value > bestValue && diff >= EPSILON

            if (better || numBestActions == 0) {
                numBestActions = 1
                bestValue = value
            } else if (diff < EPSILON) {
                numBestActions++
            }
        }

        var pickedAction = random.nextInt(numBestActions)
        logger.debug { "Pick best action chosed index $pickedAction of $possibleActions with value $bestValue" }
        for (i in possibleActions) {
            val stateAction = stateActionFunction(environment, state, i)
            val value = qTable.getOrDefault(stateAction, DEFAULT_QVALUE)
            val diff = Math.abs(value - bestValue)

            if (diff < EPSILON) {
                pickedAction--
                if (pickedAction < 0) {
                    return i
                }
            }
        }
        throw IllegalStateException("No successful action because of some logic problem.")
    }

    private fun getPossibleActions(environment: T): IntArray {
        return (0 until maxActions)
                .filter { i -> actionPossible(environment, i) }
                .toIntArray()
    }

    fun isActionPossible(environment: T, action: Int): Boolean {
        return this.actionPossible(environment, action)
    }

    override fun toString(): String {
        return "MyQLearning{" +
                "maxActions=" + maxActions +
                ", enabled=" + isEnabled +
                ", discountFactor=" + discountFactor +
                ", learningRate=" + learningRate +
                ", randomMoveProbability=" + randomMoveProbability +
                '}'.toString()
    }

}
