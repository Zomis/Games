package net.zomis.games.compose.common.gametype

import androidx.compose.runtime.Composable
import net.zomis.games.server2.ServerGames

class SupportedGames : GameTypeStore {

    /*
    Game name, composable component, play time, description, screenshot (or generated random state!),
    players count (from server),
    special view types for Log
    some rule examples / rule descriptions
    Link to Board Game Geek

    Other Specials from Vue Client:
    - Set: resetActions = false
    */
    private val games = mutableMapOf<String, GameTypeDetails>()

    override val gameTypes: List<String> get() = games.keys.toList()
    override fun getGameType(gameType: String): GameTypeDetails? = games[gameType]

    init {
        addGame("NoThanks") { }
        addGame("Hanabi") {}
        addGame("DSL-TTT") { }
    }

    private fun addGame(gameType: String, component: @Composable (Any) -> Unit) {
        games[gameType] = GameTypeDetailsImpl(
            gameType = gameType,
            gameEntryPoint = ServerGames.entrypoint(gameType)!!,
            component = component,
        )

    }

}