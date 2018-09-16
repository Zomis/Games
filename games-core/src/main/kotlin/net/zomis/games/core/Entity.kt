package net.zomis.games.core

import kotlin.reflect.KClass

class Entity {
    fun <T: Component> component(clazz: KClass<T>): T {
        return components.find({ clazz.isInstance(it) })!! as T
    }

    fun add(component: Component): Entity {
        components.add(component)
        return this
    }

    private val components: MutableSet<Component> = mutableSetOf()

}
