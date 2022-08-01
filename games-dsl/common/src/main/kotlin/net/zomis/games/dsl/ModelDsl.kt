package net.zomis.games.dsl

import net.zomis.games.PlayerEliminationsWrite
import net.zomis.games.common.GameEvents
import net.zomis.games.dsl.impl.GameMarker

interface GameEventsExecutor {
    fun <E> fire(executor: GameEvents<E>, event: E)
}

interface GameFactoryScope<C> {
    fun <E: Any> config(config: GameConfig<E>): E
    val events: GameEventsExecutor
    val eliminationCallback: PlayerEliminationsWrite
    val playerCount: Int
    @Deprecated("use config method instead")
    val config: C
}
@GameMarker
interface GameModel<T: Any, C> {
    fun players(playerCount: IntRange)
    fun playersFixed(playerCount: Int)
    fun defaultConfig(creator: () -> C)
    fun init(factory: GameFactoryScope<C>.() -> T)
    fun onStart(effect: GameStartScope<T>.() -> Unit)
}
