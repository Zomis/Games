package net.zomis.games.dsl.impl

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import net.zomis.games.dsl.*
import net.zomis.games.dsl.listeners.BlockingGameListener
import net.zomis.games.listeners.ExceptionPrinter
import net.zomis.games.listeners.LimitedNextViews

class GameTestContext<T: Any>(val coroutineScope: CoroutineScope, val entryPoint: GameEntryPoint<T>, val playerCount: Int): GameTestScope<T>, GameListener {
    private val state = mutableMapOf<String, Any>()
    val setup = entryPoint.setup()
    var config: GameConfigs = setup.configs()
    var gameImpl: Game<T>? = null
    private val blocking = BlockingGameListener()
    var forwards = 0

    private suspend fun initializedGame(): Game<T> {
        if (gameImpl == null) {
            gameImpl = entryPoint.setup().startGameWithConfig(coroutineScope, playerCount, config) {
                listOf(blocking, LimitedNextViews(10), ExceptionPrinter, this)
            }
            blocking.await()
        }
        return gameImpl!!
    }

    override val game: T
        get() {
            if (forwards == 0) throw IllegalStateException("Rules might not have been executed. Run initialize() or perform/assert actions before accessing raw game state.")
            return gameImpl!!.model
        }

    override suspend fun handle(coroutineScope: CoroutineScope, step: FlowStep) {
        if (step is FlowStep.PreSetup<*>) {
            step.state.clear()
            step.state.putAll(state)
            state.clear()
        }
        if (step is FlowStep.PreMove) {
            step.state.clear()
            step.state.putAll(state)
            state.clear()
        }
    }

    override fun state(key: String, value: Any) {
        state[key] = value
    }

    override suspend fun <A : Any> action(playerIndex: Int, actionType: ActionType<T, A>, parameter: A) {
        initialize()
        blocking.awaitAndPerform(playerIndex, actionType, parameter)
        flowForward()
    }

    override suspend fun initialize() {
        if (forwards == 0) {
            forwards++
            initializedGame()
            flowForward()
        }
    }

    private suspend fun flowForward() {
        forwards++
        blocking.await()
    }

    override fun expectEquals(expected: Any, actual: Any) {
        if (expected != actual) {
            throw IllegalStateException("$actual does not equal $expected")
        }
    }

    override fun branches(branches: GameTestBranches<T>.() -> Unit) {
        TODO("Not yet implemented")
    }

    override fun expectTrue(condition: Boolean) {
        if (!condition) {
            throw IllegalStateException("$condition was not true")
        }
    }

    override suspend fun <A : Any> actionNotAllowed(playerIndex: Int, actionType: ActionType<T, A>, parameter: A) {
        initialize()
        blocking.await()
        val actionImpl = initializedGame().actions[actionType.name]
        checkNotNull(actionImpl) { "No such action name: ${actionType.name}" }
        val actionable = actionImpl.createAction(playerIndex, parameter)
        check(!actionImpl.isAllowed(actionable)) { "Action is allowed when it shouldn't be: $playerIndex $actionable" }
    }

    override suspend fun expectNoActions(playerIndex: Int) {
        initialize()
        blocking.await()
        for (actionType in initializedGame().actions.types()) {
            val actions = actionType.availableActions(playerIndex, null).take(5)
            if (actions.isNotEmpty()) {
                throw IllegalStateException("Found possible action(s) for player $playerIndex action '${actionType.name}': $actions")
            }
        }
    }

    override fun config(key: String, value: Any) {
        this.config.set(key, value)
    }

}

class GameTestCaseContext<T: Any>(val players: Int, val testContext: GameTestDsl<T>) {

    suspend fun runTests(entryPoint: GameEntryPoint<T>) {
        coroutineScope {
            val context = GameTestContext(this, entryPoint, players)
            testContext.invoke(context)
            context.gameImpl?.stop()
        }
    }

}