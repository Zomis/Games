package net.zomis.core.events

import klogging.KLoggers
import kotlin.reflect.KClass

enum class ListenerPriority { FIRST, EARLIER, EARLY, NORMAL, LATE, LATER, LAST }

class Listener<in E>(val description: String, val condition: (E) -> Boolean, val handler: EventHandler<E>) {

    private val logger = KLoggers.logger(this)

    fun run(event: E) {
        if (condition.invoke(event)) {
            logger.info { "Running '$description' for event $event" }
            handler.invoke(event)
        }
    }
}

class ListenerList<E> {

    private val logger = KLoggers.logger(this)
    private val list: MutableList<Listener<E>> = mutableListOf()

    fun add(eventHandler: Listener<E>) {
        list.add(eventHandler)
    }

    fun execute(event: E) {
        list.forEach({
            try {
                it.run(event)
            } catch (e: RuntimeException) {
                logger.error(e, "Problem when handling event $event in $it")
                throw e
            }
        })
    }

}

class EventSystem {

    private val logger = KLoggers.logger(this)
    private val listeners: MutableMap<KClass<Any>, ListenerList<Any>> = HashMap()

    fun <E : Any> listen(description: String, priority: ListenerPriority, clazz: KClass<E>,
         condition: (E) -> Boolean, handler: EventHandler<E>) {
        logger.info("Add Listener with priority $priority to $clazz: $handler")
        val list: ListenerList<E> = listeners.getOrElse(clazz as KClass<Any>, { ListenerList<E>() }) as ListenerList<E>
        list.add(Listener(description, condition, handler))
        listeners[clazz] = list as ListenerList<Any>
    }

    fun <E : Any> listen(description: String, clazz: KClass<E>, condition: (E) -> Boolean, handler: EventHandler<E>) {
        return listen(description, ListenerPriority.NORMAL, clazz, condition, handler)
    }

    fun <E : Any> addListener(clazz: KClass<E>, handler: EventHandler<E>) {
        return listen(handler.toString(), ListenerPriority.NORMAL, clazz, {true}, handler)
    }

    fun <E : Any> execute(event: E) {
        logger.info("Execute: $event")
        val kclass = event::class as KClass<Any>
        listeners[kclass]?.execute(event)
    }

    fun with(registrator: EventRegistrator): EventSystem {
        registrator.invoke(this)
        return this
    }

}

typealias EventHandler<E> = (E) -> Unit
typealias EventRegistrator = (EventSystem) -> Unit
