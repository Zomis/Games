package net.zomis.games.dsl

interface GameTestBranches<T: Any> {
    fun branch(name: String, block: GameTest<T>.() -> Unit)
}

interface GameTest<T: Any> {
    val game: T
    fun state(key: String, value: Any)
    fun <A: Any> action(playerIndex: Int, action: ActionType<T, A>, parameter: A)
    fun expectEquals(expected: Any, actual: Any)
    fun expectTrue(condition: Boolean)
    fun branches(branches: GameTestBranches<T>.() -> Unit)
}
