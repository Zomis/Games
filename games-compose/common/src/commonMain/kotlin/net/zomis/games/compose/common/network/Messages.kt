package net.zomis.games.compose.common.network

import net.zomis.games.WinResult
import net.zomis.games.server2.invites.PlayerInfo
import net.zomis.games.server2.invites.PlayerInfoId
import kotlin.reflect.KClass

data class PlayerWithOptions(
    val id: String, val name: String?, val picture: String?,
    val playerOptions: Any?
) {
    fun asPlayerInfo(): PlayerInfo = PlayerInfo(playerId = id, name = name, picture = picture)
}

sealed class Message(val type: String) {

    data class ErrorMessage(val error: String) : Message("error")
    internal data class LobbyMessageInternal(val users: Map<String, List<PlayerInfoId>>) : Message("Lobby") {
        fun toLobbyMessage() = LobbyMessage(users.mapValues { it.value.map(PlayerInfoId::toPlayerInfo) })
    }
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
        val host: PlayerInfoId,
        val players: List<PlayerWithOptions>,
        val invited: List<PlayerInfoId>
    ) : Message("InviteView")

    data class InviteStatus(val inviteId: String, val playerId: String, val status: String) : Message("InviteStatus")
    data class InvitePrepare(val gameType: String, val playersMin: Int, val playersMax: Int, val config: Any?) : Message("InvitePrepare")
    data class InviteResponse(val inviteId: String, val playerId: String, val accepted: Boolean) : Message("InviteResponse")

    data class GameStarted(
        val gameType: String, val gameId: String,
        val access: Map<String, String>,
        val players: List<PlayerInfoId>
    ) : Message("GameStarted") {
        fun indexAccess(): Map<Int, String> = access.mapKeys { it.key.toInt() }
    }
    data class GameInfo(
        val gameType: String, val gameId: String,
        val access: Map<String, String>,
        val players: List<PlayerInfo>
    ) : Message("GameInfo")

    sealed class GameMessage(type: String) : Message(type) {
        abstract val gameType: String
        abstract val gameId: String

        data class ActionLog(override val gameType: String, override val gameId: String, val private: Boolean, val parts: List<Any>) : GameMessage("ActionLog")
        data class UpdateView(override val gameType: String, override val gameId: String) : GameMessage("UpdateView")
        data class GameReady(override val gameType: String, override val gameId: String) : GameMessage("GameReady")
        data class GameEnded(override val gameType: String, override val gameId: String) : GameMessage("GameEnded")
        data class GameView(override val gameType: String, override val gameId: String, val viewer: Int, val view: Any) : GameMessage("GameView")
        data class GameMove(override val gameType: String, override val gameId: String, val player: Int, val moveType: String) : GameMessage("GameMove")
        data class ActionList(override val gameType: String, override val gameId: String, val playerIndex: Int, val actions: Any) : GameMessage("ActionList")
        data class PlayerEliminated(override val gameType: String, override val gameId: String, val player: Int, val winner: Boolean, val winResult: WinResult, val position: Int) : GameMessage("PlayerEliminated")

    }
    companion object {
        fun messageType(type: String): KClass<out Message>? {
            return when (type) {
                "Lobby" -> LobbyMessageInternal::class
                "Auth" -> AuthMessage::class
                "LobbyChange" -> LobbyChangeMessage::class
                "Invite" -> Invite::class
                "InviteCancelled" -> InviteCancelled::class
                "InviteView" -> InviteView::class
                "InvitePrepare" -> InvitePrepare::class
                "InviteStatus" -> InviteStatus::class
                "InviteResponse" -> InviteResponse::class
                "ActionLog" -> GameMessage.ActionLog::class
                "GameStarted" -> GameStarted::class
                "GameInfo" -> GameInfo::class
                "UpdateView" -> GameMessage.UpdateView::class
                "GameReady" -> GameMessage.GameReady::class
                "GameEnded" -> GameMessage.GameEnded::class
                "GameView" -> GameMessage.GameView::class
                "GameMove" -> GameMessage.GameMove::class
                "ActionList" -> GameMessage.ActionList::class
                "PlayerEliminated" -> GameMessage.PlayerEliminated::class
                "error" -> ErrorMessage::class
                else -> null
            }
        }
    }

}