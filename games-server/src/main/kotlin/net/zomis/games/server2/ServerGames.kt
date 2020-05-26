package net.zomis.games.server2

import net.zomis.games.dsl.*
import net.zomis.games.dsl.impl.GameSetupImpl
import net.zomis.games.dsl.sourcedest.ArtaxGame
import net.zomis.games.dsl.sourcedest.TTSourceDestinationGames
import net.zomis.games.impl.DungeonMayhemDsl
import net.zomis.games.impl.HanabiGame
import net.zomis.games.impl.SetGame
import net.zomis.games.impl.SkullGame

object ServerGames {

    val games = mutableMapOf<String, Any>(
        "DSL-TTT" to DslTTT().game,
        "DSL-Connect4" to DslTTT().gameConnect4,
        "DSL-UTTT" to DslTTT().gameUTTT,
        "DSL-Reversi" to DslTTT().gameReversi,
        "Quixo" to TTSourceDestinationGames().gameQuixo,
        "Artax" to ArtaxGame.gameArtax,
        "Hanabi" to HanabiGame.game,
        "Set" to SetGame.game,
        "Skull" to SkullGame.game,
        "Dungeon Mayhem" to DungeonMayhemDsl.game,
        "Splendor" to DslSplendor.splendorGame,
        "DSL-TTT3D" to DslTTT3D().game,
        "DSL-UR" to DslUR().gameUR
    )

    fun setup(gameType: String): GameSetupImpl<Any>? {
        val spec = games[gameType] as GameSpec<Any>? ?: return null
        return GameSetupImpl(spec)
    }

}
