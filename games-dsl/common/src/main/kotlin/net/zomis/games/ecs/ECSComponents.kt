package net.zomis.games.ecs

import net.zomis.games.PlayerEliminationsRead
import net.zomis.games.components.Point
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

internal object HiddenECSValue
typealias ECSViewFunction<T> = (ECSViewScope).(T) -> Any?

class ECSComponentBuilder<T: Any>(
    name: String,
    override val key: ECSAccessor<T>,
    var default: (parent: ECSComponent<T>) -> T? = {null}
): ECSAccessor<T>(name) {
    init {
        require(name == key.name) { "$name does not match ${key.name}" }
    }
    private val privateViews = mutableMapOf<Int, ECSViewFunction<T>>()
    private var publicView: ECSViewFunction<T> = { it }
    private var combiner: (old: T, new: T) -> T = { _, _ ->
        throw IllegalStateException("Unable to combine components for $name with key $key")
    }

    fun value(value: T): ECSComponentBuilder<T> {
        default = { value }
        return this
    }

    fun new(key: String): ECSComponentBuilder<T> {
        val accessor = ECSAccessor<T>(key)
        return ECSComponentBuilder(key, accessor)
    }

    fun view(viewFunction: ECSViewScope.(T) -> Any): ECSComponentBuilder<T> {
        this.publicView = { viewFunction.invoke(this, it) }
        return this
    }
    fun privateView(playerIndices: List<Int>, view: (T) -> Any): ECSComponentBuilder<T> {
        playerIndices.forEach { index ->
            privateViews[index] = { view.invoke(it) }
        }
        return this
    }
    fun privateView(playerIndex: Int, view: (T) -> Any): ECSComponentBuilder<T> = this.privateView(listOf(playerIndex), view)
    fun publicView(view: (T) -> Any): ECSComponentBuilder<T> {
        this.publicView = { view.invoke(it) }
        return this
    }

    fun hiddenView(): ECSComponentBuilder<T> {
        this.publicView = { HiddenECSValue }
        return this
    }

    fun combiner(combiner: (old: T, new: T) -> T): ECSComponentBuilder<T> {
        this.combiner = combiner
        return this
    }

    fun buildFor(owner: ECSSimpleEntity): ECSComponent<T> {
        // TODO: Use an `ECSCombinableComponent` interface to combine multiple components of the same type (ECSActions, ECSRules...)
        val old = owner.getOrNull(key)
        return ECSComponentImpl<T>(owner, this.name).also {
            it.privateViews = this.privateViews
            it.publicView = this.publicView
            val new = this.default.invoke(it)
            if (old == null) {
                it.component = new
            } else {
                checkNotNull(new) { "Unable to combine component $name with key $key with an old value $old but new value null" }
                it.component = combiner.invoke(old, new)
            }
        }
    }
}
