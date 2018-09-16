package net.zomis.games.core

import net.zomis.core.events.EventRegistrator
import net.zomis.core.events.EventSystem

typealias GameSystem = EventRegistrator

class Game {

    val core: Entity = Entity()
    val events = EventSystem()

    fun system(system: GameSystem) {

    }

}
