package net.zomis.games.dsl

import net.zomis.games.PlayerEliminationCallback

interface GameFactoryScope<C> {
    val eliminationCallback: PlayerEliminationCallback
    val playerCount: Int
    val config: C
}
interface GameModel<T, C> {
    fun players(playerCount: IntRange)
    fun defaultConfig(creator: () -> C)
    fun init(factory: GameFactoryScope<C>.(C?) -> T)
    fun onStart(effect: ReplayableScope.(T) -> Unit)
}
