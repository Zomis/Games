package net.zomis.games.compose.common.mfe

import com.arkivanov.essenty.parcelable.Parcelable
import com.arkivanov.essenty.parcelable.Parcelize
import net.zomis.games.compose.common.network.ClientConnection
import net.zomis.games.compose.common.network.Message

sealed interface Configuration : Parcelable {

    @Parcelize
    data class Game(
        val gameStarted: Message.GameStarted,
        val connection: ClientConnection,
    ) : Configuration

    enum class MenuChoice {
        Singleplayer, Multiplayer, Challenge
    }
    data class Menu(
        val menu: MenuChoice?
    ) : Configuration

}