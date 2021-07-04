package net.zomis.games.dsl

interface GameTestBranches<T: Any> {
    fun branch(name: String, block: GameTest<T>.() -> Unit)
}

interface GameTest<T: Any> {
    val game: T
    fun state(key: String, value: Any)
    suspend fun initialize()
    suspend fun <A: Any> action(playerIndex: Int, actionType: ActionType<T, A>, parameter: A)
    fun expectEquals(expected: Any, actual: Any)
    fun expectTrue(condition: Boolean)
    fun branches(branches: GameTestBranches<T>.() -> Unit)
    suspend fun <A: Any> actionNotAllowed(playerIndex: Int, actionType: ActionType<T, A>, parameter: A)
    suspend fun expectNoActions(playerIndex: Int)
    fun config(config: Any)
    suspend fun initializeGame(): T
}
