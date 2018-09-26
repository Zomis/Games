package net.zomis.games.core

import net.zomis.core.events.EventRegistrator
import net.zomis.core.events.EventSystem

typealias GameSystem = EventRegistrator

class Game {

    private val entitiesById = mutableMapOf<String, Entity>()
    val core: Entity = createEntity()
    private val events = EventSystem()
    private var id = 0

    fun createEntity(): Entity {
        val result = Entity(this, (id++).toString())
        entitiesById[result.id] = result
        return result
    }

    fun entityById(id: String): Entity? {
        return entitiesById[id]
    }

    fun system(system: GameSystem) {
        events.with(system)
    }

    fun <E: Any> execute(event: E) {
        this.events.execute(event)
    }

}
