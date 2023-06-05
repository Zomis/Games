package net.zomis.games.compose.common

class ClientConfig {
    companion object {
        const val PRODUCTION_URL = "wss://games.zomis.net/backend/websocket"
        const val LOCAL_URL = "ws://127.0.0.1:42638/websocket"
    }

    val websocketUrl = LOCAL_URL
}
