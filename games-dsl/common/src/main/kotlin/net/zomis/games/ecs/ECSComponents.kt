package net.zomis.games.ecs

import net.zomis.games.PlayerEliminationsRead
import net.zomis.games.components.Point
import net.zomis.games.components.grids.Grid
import net.zomis.games.dsl.GameConfigs
import net.zomis.games.dsl.GameEventsExecutor

object PlayerIndex: ECSAccessor<Int>("playerIndex")
object Point: ECSAccessor<Point>("point") {
    fun withValue(point: Point): ECSComponentBuilder<Point> {
        return ECSComponentBuilder(this.name, this) { point }
    }
}
object Grid: ECSAccessor<ECSGrid>("grid")
object ECSConfigs: ECSAccessor<GameConfigs>("configs")
object ECSEliminations: ECSAccessor<PlayerEliminationsRead>("eliminations")
object ECSEvents: ECSAccessor<GameEventsExecutor>("events")

open class ECSAccessor<T>(val name: String) {
    open val key: ECSAccessor<T> get() = this
}

class ECSComponentBuilder<T>(name: String, override val key: ECSAccessor<T>, var default: (parent: ECSComponent<T>) -> T? = {null}): ECSAccessor<T>(name) {
    init {
        require(name == key.name) { "$name does not match ${key.name}" }
    }
    fun value(value: T): ECSComponentBuilder<T> {
        default = { value }
        return this
    }

    fun new(key: String): ECSComponentBuilder<T> {
        val accessor = ECSAccessor<T>(key)
        return ECSComponentBuilder(key, accessor)
    }
}
