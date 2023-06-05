package net.zomis.games.compose.server2

import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.parcelable.Parcelable
import com.arkivanov.essenty.parcelable.Parcelize
import net.zomis.games.compose.common.gametype.GameTypeDetails
import net.zomis.games.compose.common.network.ClientConnection
import net.zomis.games.compose.common.network.Message
import net.zomis.games.server2.invites.PlayerInfo

sealed interface Configuration : Parcelable {

    @Parcelize
    object Login : Configuration

    // GameTypeDatabase
    // GameDatabase

    @Parcelize
    data class Home(val connection: ClientConnection) : Configuration

    @Parcelize
    data class CreateInvite(
        val invitePrepare: Message.InvitePrepare,
        val gameTypeDetails: GameTypeDetails,
        val connection: ClientConnection,
    ) : Configuration

    @Parcelize
    data class ViewInvite(
        val invite: Message.InviteView,
        val availablePlayers: Value<List<PlayerInfo>>,
        val connection: ClientConnection,
    ) : Configuration

    @Parcelize
    data class Game(
        val gameStarted: Message.GameStarted,
        val connection: ClientConnection,
    ) : Configuration

    enum class MenuChoice {
        Singleplayer, Multiplayer, Challenge
    }
    data class Menu(
        val menu: MenuChoice
    ) : Configuration

}