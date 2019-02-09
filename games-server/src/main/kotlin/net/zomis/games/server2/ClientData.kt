package net.zomis.games.server2

import net.zomis.core.events.EventSystem

@Deprecated("use new Features system instead")
class ClientData<T> {

    private val map = mutableMapOf<Client, T>()

    fun setForClient(value: T, client: Client) {
        map[client] = value
    }

    fun get(client: Client): T? {
        return map[client]
    }

    fun register(events: EventSystem) {
        events.listen("clear ClientData", ClientDisconnected::class, {true}, {
            this.map.remove(it.client)
        })
    }

    fun entries(): MutableSet<out Map.Entry<Client, T>> {
        return map.entries
    }

}