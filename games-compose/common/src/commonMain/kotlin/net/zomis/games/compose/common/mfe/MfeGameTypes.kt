package net.zomis.games.compose.common.mfe

import net.zomis.games.compose.common.PlatformTools
import net.zomis.games.compose.common.gametype.GameTypeDetails
import net.zomis.games.compose.common.gametype.GameTypeStore
import net.zomis.games.compose.common.gametype.SupportedGames
import net.zomis.games.impl.minesweeper.Flags
import net.zomis.games.impl.minesweeper.specials.OpenFieldChallenge

class MfeGameTypes(platformTools: PlatformTools) {
    private val supportedGames = SupportedGames(platformTools)

    val defaultGameType = supportedGames.getGameType(Flags.game.name)!!
    val ofcGameType = supportedGames.getGameType(OpenFieldChallenge.game.name)!!

    fun getGameType(gameType: String): GameTypeDetails? = supportedGames.getGameType(gameType)

}