package net.zomis.games.server2.games

import net.zomis.core.events.EventSystem
import net.zomis.games.Features
import net.zomis.games.server2.ClientJsonMessage
import net.zomis.games.server2.getTextOrDefault

@Deprecated("Clients should stop using this")
class ObserverSystem {

    fun setup(features: Features, events: EventSystem) {
        events.listen("Fire Observer Request", ClientJsonMessage::class, {
            it.data.getTextOrDefault("type", "") == "observer"
        }, {
            TODO("Deprecated")
        })
    }

}