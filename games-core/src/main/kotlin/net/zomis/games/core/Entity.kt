package net.zomis.games.core

import kotlin.reflect.KClass

data class UpdateEntityEvent(val entity: Entity, val componentClass: KClass<*>, val value: Component)

class Entity(val world: World, val id: String) {

    fun <T: Component> component(clazz: KClass<T>): T {
        val value = components.find({ clazz.isInstance(it) })
        return if (value != null) value as T else
            throw NullPointerException("No such component: $clazz on $this. Available components are ${components.map { it::class }}")
    }

    fun <T: Component> componentOrNull(clazz: KClass<T>): T? {
        return components.find({ clazz.isInstance(it) }) as T?
    }

    fun components(): Set<Component> {
        return components
    }

    fun add(component: Component): Entity {
        components.add(component)
        return this
    }

    private val components: MutableSet<Component> = mutableSetOf()

    override fun toString(): String {
        return "[Entity $id]"
    }

    fun <T: Component> updateComponent(component: KClass<T>, perform: (T) -> Unit): T {
        val value = component(component)
        perform.invoke(value)
        world.execute(UpdateEntityEvent(this, component, value))
        return value
    }

}
