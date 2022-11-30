package net.zomis.games.dsl

import net.zomis.games.PlayerEliminationsWrite
import net.zomis.games.api.UsageScope
import net.zomis.games.common.GameEvents
import net.zomis.games.dsl.events.EventsHandling
import net.zomis.games.dsl.impl.GameMarker

@Deprecated("old events class, use Event class instead")
interface GameEventsExecutor {
    fun <E> fire(executor: GameEvents<E>, event: E)
}

interface GameFactoryScope<GameModel: Any, C> : UsageScope {
    fun <E: Any> config(config: GameConfig<E>): E
    val events: EventsHandling<GameModel>
    val oldEvents: GameEventsExecutor
    val eliminationCallback: PlayerEliminationsWrite
    val playerCount: Int
    @Deprecated("use config method instead")
    val config: C
    val configs: GameConfigs
}
@GameMarker
interface GameModelScope<T: Any, C> : UsageScope {
    fun players(playerCount: IntRange)
    fun playersFixed(playerCount: Int)
    fun defaultConfig(creator: () -> C)
    fun init(factory: GameFactoryScope<T, C>.() -> T)
    fun onStart(effect: GameStartScope<T>.() -> Unit)
}
