package net.zomis.games.compose.common.network

sealed class ClientToServerMessage(val route: String) {

    class AuthRequest(authProvider: String, val token: String) : ClientToServerMessage("auth/$authProvider")
    class JoinLobby(val gameTypes: Set<String>, val maxGames: Int) : ClientToServerMessage("lobby/join")
    object ListLobby : ClientToServerMessage("lobby/list")

}