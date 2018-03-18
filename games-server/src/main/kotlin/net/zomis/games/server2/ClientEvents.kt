package net.zomis.games.server2

data class ClientMessage(val client: Client, val decoded: String)
data class ClientConnected(val client: Client)
data class ClientDisconnected(val client: Client)
