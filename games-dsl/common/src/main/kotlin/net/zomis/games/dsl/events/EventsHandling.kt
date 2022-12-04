package net.zomis.games.dsl.events

import net.zomis.games.api.UsageScope
import net.zomis.games.dsl.flow.GameMetaScope

enum class EventPriority {
    EARLIEST,
    EARLIER,
    EARLY,
    NORMAL,
    LATE,
    LATER,
    LATEST
}

interface EventSource
class SimpleEventSource: EventSource
interface EventFactory<E: Any> : EventSource {
    operator fun invoke(value: E)
}

interface GameEventEffectScope<GameModel: Any, E: Any>: UsageScope {
    val effectSource: EventSource
    val event: E
    val meta: GameMetaScope<GameModel>
    val replayable get() = meta.replayable
}

interface EventListener {
    fun conditionCheck(scope: GameEventEffectScope<Any, Any>): Boolean = true
    fun mutate(scope: GameEventEffectScope<Any, Any>, source: EventSource, event: Any): Any = event
    fun execute(scope: GameEventEffectScope<Any, Any>)
}

class EventsHandling<GameModel: Any>(val metaScope: GameMetaScope<GameModel>) {

    private val onEvent = mutableMapOf<EventPriority, MutableList<EventListener>>()

    fun fireEvent(source: EventSource, eventValue: Any) {
        var event = eventValue

        val context = object : GameEventEffectScope<Any, Any> {
            override val effectSource: EventSource = source
            override val event: Any get() = eventValue
            override val meta: GameMetaScope<Any> = metaScope as GameMetaScope<Any>
        }

        for (priority in EventPriority.values()) {
            val currentPriorityListeners = onEvent[priority] ?: continue

            // Step 1. Possibly prevent event
            if (!currentPriorityListeners.all { it.conditionCheck(context) }) {
                return
            }

            // Step 2. Change/Mutate event
            for (listener in currentPriorityListeners) {
                event = listener.mutate(context, source, event)
            }

            // Step 3. Execute event
            for (listener in currentPriorityListeners.toList()) {
                listener.execute(context)
            }
        }
    }

    fun addEventListener(priority: EventPriority, listener: EventListener) {
        onEvent.getOrPut(priority) { mutableListOf() }.add(listener)
    }

}