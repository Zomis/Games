package net.zomis.games.compose.common.network

sealed class ClientToServerMessage(val route: String) {

    class AuthRequest(authProvider: String, val token: String) : ClientToServerMessage("auth/$authProvider")
    class JoinLobby(val gameTypes: Set<String>, val maxGames: Int) : ClientToServerMessage("lobby/join")
    object ListLobby : ClientToServerMessage("lobby/list")

    class Invite(val gameType: String, val invite: List<String>) : ClientToServerMessage("invites/invite")
    class InviteView(inviteId: String) : ClientToServerMessage("invites/$inviteId/view")
    class InviteRespond(inviteId: String, val accepted: Boolean) : ClientToServerMessage("invites/$inviteId/respond")
    class InviteCancel(inviteId: String) : ClientToServerMessage("invites/$inviteId/cancel")
    class InvitePrepare(val gameType: String) : ClientToServerMessage("invites/prepare")

    class InvitePrepareStart(val gameType: String, val options: Any, val gameOptions: Any) : ClientToServerMessage("invites/start")
    class InviteStart(inviteId: String) : ClientToServerMessage("invites/$inviteId/start")
    class InviteSend(inviteId: String, val invite: List<String>) : ClientToServerMessage("invites/$inviteId/send")

    class GameJoin(gameType: String, gameId: String) : ClientToServerMessage("games/$gameType/$gameId/join")
    class GameView(gameType: String, gameId: String, val playerIndex: Int, val actionType: String?, val chosen: List<Any>?) : ClientToServerMessage("games/$gameType/$gameId/view")
    class GameActionPerform(gameType: String, gameId: String, val playerIndex: Int, val moveType: String, val chosen: List<Any>?) : ClientToServerMessage("games/$gameType/$gameId/action")
    @Deprecated("Prefer GameActionPerform instead")
    class GameActionMove(gameType: String, gameId: String, val playerIndex: Int, val moveType: String, val move: Any?) : ClientToServerMessage("games/$gameType/$gameId/move")

    @Deprecated("Actions should be made part of view")
    class GameActionList(gameType: String, gameId: String, val playerIndex: Int, val chosen: List<Any>) : ClientToServerMessage("games/$gameType/$gameId/actionList")

}