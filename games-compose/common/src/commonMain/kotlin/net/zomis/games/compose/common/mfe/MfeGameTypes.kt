package net.zomis.games.compose.common.mfe

import net.zomis.games.compose.common.PlatformTools
import net.zomis.games.compose.common.gametype.GameTypeDetails
import net.zomis.games.compose.common.gametype.GameTypeStore
import net.zomis.games.compose.common.gametype.SupportedGames
import net.zomis.games.impl.minesweeper.Flags

class MfeGameTypes(private val platformTools: PlatformTools) : GameTypeStore {
    override val gameTypes: List<String>
        get() = emptyList()

    override fun getGameType(gameType: String): GameTypeDetails? {
        return null
    }

    val defaultGameType = SupportedGames(platformTools).getGameType(Flags.game.name)!!

}