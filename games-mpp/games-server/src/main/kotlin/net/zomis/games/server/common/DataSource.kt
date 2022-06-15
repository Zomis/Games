package net.zomis.games.server.common

interface PlayerDataSource {
    fun playerId()
    fun oauth()
}
interface GamesDataSource {
    fun unfinishedGames(gameType: String)
    fun unfinishedPlayerGames(playerId: String)

    fun allGames()

}
