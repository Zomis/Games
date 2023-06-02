package net.zomis.games.compose.common.mfe

import com.arkivanov.essenty.parcelable.Parcelable
import com.arkivanov.essenty.parcelable.Parcelize
import net.zomis.games.compose.common.mfe.challenges.Challenge
import net.zomis.games.compose.common.network.ClientConnection
import net.zomis.games.compose.common.network.Message
import net.zomis.games.impl.minesweeper.Flags

sealed interface Configuration : Parcelable {

    @Parcelize
    object Menu : Configuration

    @Parcelize
    data class LocalGame(
        val ai: Flags.AI?
    ) : Configuration

    @Parcelize
    data class ChallengeConfig(val challenge: Challenge) : Configuration

    @Parcelize
    data class OnlineGame(
        val gameStarted: Message.GameStarted,
        val connection: ClientConnection,
    ) : Configuration

}