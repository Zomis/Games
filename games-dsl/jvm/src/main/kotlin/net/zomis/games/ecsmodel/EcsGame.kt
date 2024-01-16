package net.zomis.games.ecsmodel

import kotlinx.coroutines.flow.StateFlow
import kotlin.reflect.KProperty0
import kotlin.reflect.jvm.isAccessible

class EcsGame<T : GameModelEntity>(
    val root: T,
    val gameState: Game,
    var playerCount: Int,
    // configs: GameConfigs,
) {

    fun copy(): EcsGame<T> = TODO()
    fun <T> stateFor(property: KProperty0<T>): StateFlow<T>? {
        val delegate = property.delegate as? GamePropertyDelegate<T>
        println("stateFor($property) returns delegate: $delegate")
        return delegate?.stateFlow()
    }

}

val KProperty0<*>.delegate: Any? get() {
    this.isAccessible = true
    return this.getDelegate()
}
