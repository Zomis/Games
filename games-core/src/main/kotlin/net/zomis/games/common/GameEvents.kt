package net.zomis.games.common

import net.zomis.games.dsl.GameEventsExecutor

typealias GameEventsListener<T> = (T) -> Unit
class GameEvents<T>(private val events: GameEventsExecutor) {

    fun fire(event: T): T {
        events.fire(this, event)
        return event
    }

}
