package net.zomis.games.server2

import net.zomis.games.dsl.GameSpec
import net.zomis.games.dsl.impl.GameSetupImpl
import net.zomis.games.impl.*
import net.zomis.games.impl.ttt.*

object ServerGames {

    val games = mutableMapOf<String, Any>(
        "Set" to SetGame.game,
        "Avalon" to ResistanceAvalonGame.game,
        "LiarsDice" to LiarsDiceGame.game,
        "Dungeon Mayhem" to DungeonMayhemDsl.game,
        "Skull" to SkullGame.game,
        "Splendor" to DslSplendor.splendorGame,
        "Hanabi" to HanabiGame.game,
        "Artax" to ArtaxGame.gameArtax,
        "Quixo" to TTSourceDestinationGames.gameQuixo,
        "DSL-TTT3D" to TTT3DGame.game,
        "DSL-UTTT" to DslTTT.gameUTTT,
        "DSL-Reversi" to DslTTT.gameReversi,
        "DSL-Connect4" to DslTTT.gameConnect4,
        "DSL-TTT" to DslTTT.game,
        "DSL-UR" to DslUR.gameUR
    )

    fun setup(gameType: String): GameSetupImpl<Any>? {
        val spec = games[gameType] as GameSpec<Any>? ?: return null
        return GameSetupImpl(spec)
    }

}
