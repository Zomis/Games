package net.zomis.games.dsl

import net.zomis.games.PlayerEliminationCallback
import net.zomis.games.common.GameEvents

interface GameEventsExecutor {
    fun fire(executor: GameEvents<*>, event: Any?)
}

interface GameFactoryScope<C> {
    val events: GameEventsExecutor
    val eliminationCallback: PlayerEliminationCallback
    val playerCount: Int
    val config: C
}
interface GameModel<T, C> {
    fun players(playerCount: IntRange)
    fun playersFixed(playerCount: Int)
    fun defaultConfig(creator: () -> C)
    fun init(factory: GameFactoryScope<C>.(C?) -> T)
    @Deprecated("use actionRules or gameRules instead and put game setup logic there")
    fun onStart(effect: ReplayableScope.(T) -> Unit)
}
