package net.zomis.games.dsl

import net.zomis.games.PlayerEliminationsWrite
import net.zomis.games.common.GameEvents

interface GameEventsExecutor {
    fun <E> fire(executor: GameEvents<E>, event: E)
}

interface GameFactoryScope<C> {
    val events: GameEventsExecutor
    val eliminationCallback: PlayerEliminationsWrite
    val playerCount: Int
    val config: C
}
interface GameModel<T: Any, C> {
    fun players(playerCount: IntRange)
    fun playersFixed(playerCount: Int)
    fun defaultConfig(creator: () -> C)
    fun init(factory: GameFactoryScope<C>.(C?) -> T)
    fun onStart(effect: GameStartScope<T>.() -> Unit)
}
