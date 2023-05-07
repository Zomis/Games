package net.zomis.games.compose.common.network

import net.zomis.games.server2.invites.PlayerInfo

sealed class Message(val type: String) {

    class LobbyMessage(val users: Map<String, List<PlayerInfo>>) : Message("Lobby")
    data class AuthMessage(val playerId: String, val name: String, val picture: String, val cookie: String?) : Message("Auth")

}