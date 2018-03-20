package net.zomis.core.events

import klogging.KLoggers
import kotlin.reflect.KClass

enum class ListenerPriority { FIRST, EARLIER, EARLY, NORMAL, LATE, LATER, LAST }

class ListenerList<E> {

    private val list: MutableList<EventHandler<E>> = mutableListOf()

    fun add(eventHandler: EventHandler<E>) {
        list.add(eventHandler)
    }

    fun execute(event: E) {
        list.forEach({ it.invoke(event) })
    }

}

class EventSystem {

    private val logger = KLoggers.logger(this)
    private val listeners: MutableMap<KClass<Any>, ListenerList<Any>> = HashMap()

    fun <E : Any> addListener(clazz: KClass<E>, handler: EventHandler<E>) {
        addListener(clazz, handler, ListenerPriority.NORMAL)
    }

    fun <E : Any> addListener(clazz: KClass<E>, handler: EventHandler<E>, priority: ListenerPriority) {
        logger.info("Add Listener with priority $priority to $clazz: $handler")
        val list: ListenerList<E> = listeners.getOrElse(clazz as KClass<Any>, { ListenerList<E>() }) as ListenerList<E>
        list.add(handler)
        listeners[clazz] = list as ListenerList<Any>
    }

    fun <E : Any> execute(event: E) {
        logger.info("Execute: $event")
        val kclass = event::class as KClass<Any>
        listeners[kclass]?.execute(event)
    }

}

typealias EventHandler<E> = (E) -> Unit
