package net.zomis.games.ecsmodel

class RPath // Entity/Resource path, with possible wildcards and conditions
class Link<T>(val value: T)

interface Game {
    // has a map with ALL GAME STATE.
    val test42: Int get() = 42
}

interface EcsModelGameSetupScope {

}

class EcsGameSpec<T : GameModelEntity>(val name: String, val dsl: EcsGameSpecScope<T>.() -> Unit)

interface EcsGameSpecScope<T : GameModelEntity> {
    fun playerCount(range: IntRange)
    fun create(dsl: context(Game) (EcsModelGameSetupScope).() -> T)
}

object EcsGameApi {

    fun <T : GameModelEntity> create(name: String, dsl: EcsGameSpecScope<T>.() -> Unit): EcsGameSpec<T> {
        return EcsGameSpec(name, dsl)
    }

}