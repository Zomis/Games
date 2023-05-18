package net.zomis.games.compose.common.gametype

interface GameTypeStore {

    val gameTypes: List<String>
    fun getGameType(gameType: String): GameTypeDetails?

}
