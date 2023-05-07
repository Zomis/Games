package net.zomis.games.compose.common

import net.zomis.games.server2.invites.PlayerInfo

object TestData {

    val testPlayerInfo = PlayerInfo("abc", "Test2", "https://www.gravatar.com/avatar/b9b9f80ddf77c6894883152d7c5c45d2?s=128&d=identicon")
    val playerInfoList = (1..32).map {
        PlayerInfo("id-$it", "Name$it", "https://www.gravatar.com/avatar/${it.toString(16).padStart(32, padChar = '0')}?s=128&d=identicon")
    }

}