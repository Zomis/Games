package net.zomis.games.ecsmodel

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import net.zomis.games.dsl.listeners.BlockingGameListener
import net.zomis.games.impl.alchemists.AlchemistsDelegationGame
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty0
import kotlin.reflect.jvm.isAccessible

class EcsModelConsole {
    val t = 42

    fun <T : GameModelEntity> play(game: EcsGameSpec<T>): EcsGame<T> {
        val await = BlockingGameListener() // TODO: Write new listeners for EcsModel games


        val game = EcsGameImpl.withSpec(game).createGame(1) {
            listOf(await)
        }
        return game

//        await.awaitGameEnd()

    }

}

//context(Game)
//class D(a: Int) {
//    constructor() : this(test42) // `Game` context is not available until it has been initialized
//    val aa by lazy { a }
//}
context(Game)
class E(a: Int) {
    constructor(game: Game) : this(game.test42)
    val aa by lazy { a }
}
class C {
    val a: Int by lazy { 3 } // Limitation: Can't pass to constructor.
}

suspend fun main() {
    // Subscribe to all state changes (separate .value and .flow ? or do `g.stateFor(property: KProperty0<T>): StateFlow<T>`?)
    // Private and public *properties* of entities.

    // Provide initial values
    // - Fixed properties can be passed when creating entity. `create(::MyEntity).bind(MyEntity::value, 4)`
    // !!! Just because a property is fixed/static, doesn't mean that it cannot be forcefully changed. Delegate is still used.

    // Create copy
    class A {
        var a = 42
        var b = 0
        var c = 21
    }
    A().apply {
        a = 1; b = 2; c = 3 // Limitation: Everything needs to be `var`
    }
    class B(val a: Int, val b: Int, val c: Int) // Limitation: Not possible to use delegates

    println(C()::a.delegate)

    coroutineScope {
        val g = EcsModelConsole().play(EcsModelExample.game)

        g.stateFor(g.root::energy)?.collectLatest {
            println("Energy $it")
        }
    }

}

val KProperty0<*>.delegate: Any? get() {
    this.isAccessible = true
    return this.getDelegate()!!::class
}