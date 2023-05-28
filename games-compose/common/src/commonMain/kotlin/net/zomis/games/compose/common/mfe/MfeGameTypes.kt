package net.zomis.games.compose.common.mfe

import net.zomis.games.compose.common.gametype.GameTypeDetails
import net.zomis.games.compose.common.gametype.GameTypeStore

object MfeGameTypes : GameTypeStore {
    override val gameTypes: List<String>
        get() = emptyList()

    override fun getGameType(gameType: String): GameTypeDetails? {
        return null
    }
}