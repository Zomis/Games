package net.zomis.games.server2

import net.zomis.core.events.EventSystem

class ClientsByName {

    val clientsByName = mutableMapOf<String, Client>()

    fun register(events: EventSystem) {
        events.listen("add client to ClientsByName", ClientLoginEvent::class, {true}, {
            clientsByName[it.loginName] = it.client
        })
        events.listen("remove client from ClientsByName", ClientDisconnected::class, {true}, {
            clientsByName.remove(it.client.name)
        })
    }

    fun get(name: String): Client? {
        return clientsByName[name]
    }

}