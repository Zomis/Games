package net.zomis.games.dsl

interface GameModel<T, C> {
    fun defaultConfig(creator: () -> C)
    fun init(factory: (C) -> T)
}
