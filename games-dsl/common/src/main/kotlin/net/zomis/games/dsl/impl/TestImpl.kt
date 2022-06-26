package net.zomis.games.dsl.impl

import net.zomis.games.dsl.*
import net.zomis.games.dsl.flow.GameFlowImpl

class GameTestContext<T: Any>(val entryPoint: GameEntryPoint<T>, val playerCount: Int): GameTest<T> {
    val stateKeeper = StateKeeper()
    val setup = entryPoint.setup()
    var config: GameConfigs = setup.configs()
    var gameImpl: Game<T>? = null
    var forwards = 0

    private fun initializedGame(): Game<T> {
        if (gameImpl == null) {
            stateKeeper.replayMode = true
            gameImpl = entryPoint.setup().createGameWithState(playerCount, config, stateKeeper)
        }
        return gameImpl!!
    }

    override val game: T
        get() {
            if (forwards == 0) throw IllegalStateException("Rules might not have been executed. Run initialize() or perform/assert actions before accessing raw game state.")
            return initializedGame().model
        }

    override fun state(key: String, value: Any) {
        stateKeeper.save(key, value)
        stateKeeper.replayMode = true
    }

    override suspend fun <A : Any> action(playerIndex: Int, actionType: ActionType<T, A>, parameter: A) {
        initialize()
        val game = initializedGame()
        val actionImpl = game.actions[actionType.name]
        requireNotNull(actionImpl) { "No such action name: ${actionType.name}, available actions are ${game.actions.actionTypes}" }
        val action = actionImpl.createAction(playerIndex, parameter)
        if (!actionImpl.isAllowed(action)) {
            throw IllegalStateException("Action is not allowed: $action")
        }
        if (game is GameFlowImpl<*>) {
            game.actionsInput.send(action)
        } else {
            actionImpl.perform(action)
        }
        flowForward()
    }

    override suspend fun initialize() {
        if (forwards == 0) {
            forwards++
            val game = initializedGame()
            if (game is GameFlowImpl<*>) {
                println("Initialize game, call flow forward")
                flowForward()
            }
        }
    }

    private suspend fun flowForward() {
        val game = initializedGame()
        if (game is GameFlowImpl<*>) {
            println("Test flow forward, awaiting feedback ${game.stateKeeper.lastMoveState()}")
            game.feedbackReceiverFlow().collect { output ->
                forwards++
                println("Test flow forward $forwards: $output")
            }
            println("Test flow forwarded ${game.stateKeeper.lastMoveState()}")
        } else {
            forwards++
        }
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
        val actionImpl = initializedGame().actions[actionType.name]
        requireNotNull(actionImpl) { "No such action name: ${actionType.name}" }
        val actionable = actionImpl.createAction(playerIndex, parameter)
        require(!actionImpl.isAllowed(actionable)) { "Action is allowed when it shouldn't be: $playerIndex $actionable" }
    }

    override suspend fun expectNoActions(playerIndex: Int) {
        initialize()
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
        val context = GameTestContext(entryPoint, players)
        testContext.invoke(context)
    }

}