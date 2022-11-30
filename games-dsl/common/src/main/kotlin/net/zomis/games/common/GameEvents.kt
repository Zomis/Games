package net.zomis.games.common

import net.zomis.games.dsl.GameEventsExecutor

@Deprecated("old game event style, use Event class instead")
class GameEvents<E>(private val events: GameEventsExecutor) {

    fun fire(event: E): E {
        events.fire(this, event)
        return event
    }

}
