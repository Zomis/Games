package net.zomis.games.compose.common.network

import net.zomis.games.server2.invites.PlayerInfo
import kotlin.reflect.KClass

sealed class Message(val type: String) {

    data class LobbyMessage(val users: Map<String, List<PlayerInfo>>) : Message("Lobby")
    data class AuthMessage(val playerId: String, val name: String, val picture: String, val cookie: String?) : Message("Auth")
    data class LobbyChangeMessage(
        val player: PlayerInfo,
        val action: String,
        val gameTypes: List<String>?
    ) : Message("LobbyChange") {
        fun didLeave() = action == "left"
        fun didJoin() = action == "joined"
    }

    companion object {
        fun messageType(type: String): KClass<out Message>? {
            return when (type) {
                "Lobby" -> LobbyMessage::class
                "Auth" -> AuthMessage::class
                "LobbyChange" -> LobbyChangeMessage::class
                else -> null
            }
        }
    }

}