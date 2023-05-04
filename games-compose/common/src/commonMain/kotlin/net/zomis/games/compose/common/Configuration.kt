package net.zomis.games.compose.common

import com.arkivanov.essenty.parcelable.Parcelable
import com.arkivanov.essenty.parcelable.Parcelize

sealed interface Configuration : Parcelable {

    @Parcelize
    object Login : Configuration

    // GameTypeDatabase
    // GameDatabase

    @Parcelize
    data class Home(val userInfo: String) : Configuration

    @Parcelize
    data class Game(val gameId: String) : Configuration


}