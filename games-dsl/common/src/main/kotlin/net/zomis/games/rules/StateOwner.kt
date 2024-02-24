package net.zomis.games.rules

import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

interface StateOwner {
    fun <T> state(initial: () -> T): PropertyDelegateProvider<*, ReadWriteProperty<*, T>>
    fun <T> state2(initial: () -> T): ReadWriteProperty<Any?, T>
}

object NoState : StateOwner {
    override fun <T> state(initial: () -> T): PropertyDelegateProvider<*, ReadWriteProperty<*, T>> {
        return PropertyDelegateProvider<Any?, ReadWriteProperty<*, T>> { _, _ -> NoStateDelegate(initial.invoke()) }
    }

    override fun <T> state2(initial: () -> T): ReadWriteProperty<Any?, T> {
        return NoStateDelegate(initial.invoke())
    }

    private class NoStateDelegate<T>(val value: T) : ReadWriteProperty<Any?, T> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): T = value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {}
    }
}

class StandaloneStateOwner : StateOwner {
    private val states = mutableMapOf<String, ReadWriteProperty<Any?, out Any?>>()

    override fun <T> state(initial: () -> T): PropertyDelegateProvider<*, ReadWriteProperty<*, T>> {
        return addState(initial.invoke())
    }

    override fun <T> state2(initial: () -> T): ReadWriteProperty<Any?, T> {
        return object : ReadWriteProperty<Any?, T> {
            override fun getValue(thisRef: Any?, property: KProperty<*>): T {
                return initial.invoke()
            }

            override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            }
        }
    }

    inner class Delegate<T>(var value: T): ReadWriteProperty<Any?, T> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): T {
            return this.value
        }

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            this.value = value
        }
    }
    private fun <T> addState(initial: T): PropertyDelegateProvider<*, ReadWriteProperty<*, T>> {
        return PropertyDelegateProvider<Nothing?, ReadWriteProperty<*, T>> { thisRef, property ->
            println("GameModifierImpl.addState: thisRef $thisRef")
            val propertyName = property.name
            Delegate(initial)
        }
    }

}