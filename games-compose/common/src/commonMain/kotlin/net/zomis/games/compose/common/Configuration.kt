package net.zomis.games.compose.common

import com.arkivanov.essenty.parcelable.Parcelable
import com.arkivanov.essenty.parcelable.Parcelize
import net.zomis.games.compose.common.network.ClientConnection

sealed interface Configuration : Parcelable {

    @Parcelize
    object Login : Configuration

    // GameTypeDatabase
    // GameDatabase

    @Parcelize
    data class Home(val connection: ClientConnection) : Configuration

    @Parcelize
    data class Game(val gameId: String) : Configuration


}