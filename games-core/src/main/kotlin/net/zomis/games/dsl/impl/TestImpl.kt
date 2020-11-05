package net.zomis.games.dsl.impl

import net.zomis.games.dsl.*

class GameTestContext<T: Any>(val entryPoint: GameEntryPoint<T>, val playerCount: Int): GameTest<T> {
    val stateKeeper = StateKeeper()
    val setup = entryPoint.setup()
    var config: Any = setup.getDefaultConfig()
    var gameImpl: GameImpl<T>? = null

    private fun initializedGame(): GameImpl<T> {
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

    override fun <A : Any> action(playerIndex: Int, action: ActionType<T, A>, parameter: A) {
        val actionImpl = initializedGame().actions[action.name]
        requireNotNull(actionImpl) { "No such action name: ${action.name}" }
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

}

class GameTestCaseContext<T: Any>(val players: Int, val testContext: GameTestDsl<T>) {

    fun runTests(entryPoint: GameEntryPoint<T>) {
        val context = GameTestContext(entryPoint, players)
        testContext.invoke(context)
    }

}