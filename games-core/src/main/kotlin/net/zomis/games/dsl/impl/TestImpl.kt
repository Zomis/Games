package net.zomis.games.dsl.impl

import net.zomis.games.dsl.*

class GameTestContext<T: Any>(val entryPoint: GameEntryPoint<T>, val playerCount: Int): GameTest<T> {
    val stateKeeper = StateKeeper()
    val setup = entryPoint.setup()
    var config: Any = setup.getDefaultConfig()
    var gameImpl: Game<T>? = null

    private fun initializedGame(): Game<T> {
        if (gameImpl == null) {
            stateKeeper.replayMode = true
            gameImpl = entryPoint.setup().createGameWithState(playerCount, config, stateKeeper)
        }
        return gameImpl!!
    }

    override val game: T
        get() = initializedGame().model

    override fun state(key: String, value: Any) {
        stateKeeper.save(key, value)
    }

    override fun <A : Any> action(playerIndex: Int, actionType: ActionType<T, A>, parameter: A) {
        val actionImpl = initializedGame().actions[actionType.name]
        requireNotNull(actionImpl) { "No such action name: ${actionType.name}" }
        val action = actionImpl.createAction(playerIndex, parameter)
        actionImpl.perform(action)
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

    override fun <A : Any> actionNotAllowed(playerIndex: Int, actionType: ActionType<T, A>, parameter: A) {
        val actionImpl = initializedGame().actions[actionType.name]
        requireNotNull(actionImpl) { "No such action name: ${actionType.name}" }
        val actionable = actionImpl.createAction(playerIndex, parameter)
        require(!actionImpl.isAllowed(actionable)) { "Action is allowed when it shouldn't be: $playerIndex $actionable" }
    }

    override fun expectNoActions(playerIndex: Int) {
        for (actionType in initializedGame().actions.types()) {
            val actions = actionType.availableActions(playerIndex, null).take(5)
            if (actions.isNotEmpty()) {
                throw IllegalStateException("Found possible action(s) for player $playerIndex action '${actionType.name}': $actions")
            }
        }
    }

    override fun config(config: Any) {
        this.config = config
    }

}

class GameTestCaseContext<T: Any>(val players: Int, val testContext: GameTestDsl<T>) {

    fun runTests(entryPoint: GameEntryPoint<T>) {
        val context = GameTestContext(entryPoint, players)
        testContext.invoke(context)
    }

}