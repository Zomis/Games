package net.zomis.games.server2.ws

import net.zomis.games.server2.*

interface WebsocketMessageHandler {
    fun connected(client: Client)
    fun disconnected(client: Client)
    fun incomingMessage(client: Client, message: String)
}
