package net.zomis.games.server2

import net.zomis.games.dsl.GameEntryPoint
import net.zomis.games.dsl.GameSpec
import net.zomis.games.dsl.impl.GameSetupImpl
import net.zomis.games.impl.*
import net.zomis.games.impl.alchemists.AlchemistsDelegationGame
import net.zomis.games.impl.grids.Battleship
import net.zomis.games.impl.grids.KingDomino
import net.zomis.games.impl.paths.Backgammon
import net.zomis.games.impl.ttt.*
import net.zomis.games.impl.words.Decrypto

object ServerGames {

    val games = listOf(
        AlchemistsDelegationGame.game,
        Battleship.game,
        Backgammon.game,
        KingDomino.game,
        Red7.Game.game,
        LightsOut.Game.game,
        TTTUpgrade.game,
        SpiceRoadDsl.game,
        Dixit.game,
        Decrypto.game,
        CoupRuleBased.game,
        SetGame.game,
        ResistanceAvalonGame.game,
        LiarsDiceGame.game,
        DungeonMayhemDsl.game,
        SkullGame.game,
        DslSplendor.splendorGame,
        HanabiGame.game,
        ArtaxGame.gameArtax,
        TTSourceDestinationGames.gameQuixo,
        TTT3DGame.game,
        DslTTT.gameUTTT,
        DslTTT.gameReversi,
        DslTTT.gameConnect4,
        DslTTT.game,
        DslUR.gameUR
    ).associateBy { it.name }

    fun setup(gameType: String): GameSetupImpl<Any>? {
        val spec = games[gameType] as GameSpec<Any>? ?: return null
        return GameSetupImpl(spec)
    }

    fun entrypoint(gameType: String): GameEntryPoint<Any>? {
        val spec = games[gameType] as GameSpec<Any>? ?: return null
        return GameEntryPoint(spec)
    }

}
