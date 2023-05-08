package net.zomis.games.compose.common.network

import net.zomis.games.WinResult
import net.zomis.games.server2.invites.PlayerInfo
import kotlin.reflect.KClass

data class PlayerWithOptions(
    val id: String, val name: String?, val picture: String?,
    val playerOptions: Any?
)

sealed class Message(val type: String) {

    data class ErrorMessage(val error: String) : Message("error")
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
    data class Invite(val host: String, val game: String, val inviteId: String) : Message("Invite")
    data class InviteCancelled(val inviteId: String) : Message("InviteCancelled")
    data class InviteView(
        val inviteId: String, val gameType: String, val cancelled: Boolean, val minPlayers: Int, val maxPlayers: Int,
        val options: String?,
        val gameOptions: Any?,
        val host: PlayerInfo,
        val players: List<PlayerWithOptions>,
        val invited: List<PlayerInfo>
    ) : Message("InviteView")

    data class InviteStatus(val inviteId: String, val playerId: String, val status: String) : Message("InviteStatus")
    data class InvitePrepare(val gameType: String, val playersMin: Int, val playersMax: Int, val config: Any) : Message("InvitePrepare")
    data class InviteResponse(val inviteId: String, val playerId: String, val accepted: Boolean) : Message("InviteResponse")

    data class ActionLog(val gameType: String, val gameId: String, val private: Boolean, val parts: List<Any>) : Message("ActionLog")

    data class GameStarted(
        val gameType: String, val gameId: String,
        val access: Map<String, String>,
        val players: List<PlayerInfo>
    ) : Message("GameStarted")
    data class GameInfo(
        val gameType: String, val gameId: String,
        val access: Map<String, String>,
        val players: List<PlayerInfo>
    ) : Message("GameInfo")
    data class UpdateView(val gameType: String, val gameId: String) : Message("UpdateView")
    data class GameReady(val gameType: String, val gameId: String) : Message("GameReady")
    data class GameEnded(val gameType: String, val gameId: String) : Message("GameEnded")
    data class GameView(val gameType: String, val gameId: String, val viewer: Int, val view: Any) : Message("GameView")
    data class GameMove(val gameType: String, val gameId: String, val player: Int, val moveType: String) : Message("GameMove")
    data class ActionList(val gameType: String, val gameId: String, val playerIndex: Int, val actions: Any) : Message("ActionList")
    data class PlayerEliminated(val gameType: String, val gameId: String, val player: Int, val winner: Boolean, val winResult: WinResult, val position: Int) : Message("PlayerEliminated")

    companion object {
        fun messageType(type: String): KClass<out Message>? {
            return when (type) {
                "Lobby" -> LobbyMessage::class
                "Auth" -> AuthMessage::class
                "LobbyChange" -> LobbyChangeMessage::class
                "Invite" -> Invite::class
                "InviteCancelled" -> InviteCancelled::class
                "InviteView" -> InviteView::class
                "InvitePrepare" -> InvitePrepare::class
                "InviteStatus" -> InviteStatus::class
                "InviteResponse" -> InviteResponse::class
                "ActionLog" -> ActionLog::class
                "GameStarted" -> GameStarted::class
                "GameInfo" -> GameInfo::class
                "UpdateView" -> UpdateView::class
                "GameReady" -> GameReady::class
                "GameEnded" -> GameEnded::class
                "GameView" -> GameView::class
                "GameMove" -> GameMove::class
                "ActionList" -> ActionList::class
                "PlayerEliminated" -> PlayerEliminated::class
                "error" -> ErrorMessage::class
                else -> null
            }
        }
    }

}