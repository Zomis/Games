package net.zomis.games.server2

import net.zomis.games.dsl.GameEntryPoint
import net.zomis.games.dsl.GameSpec
import net.zomis.games.dsl.impl.GameSetupImpl
import net.zomis.games.impl.*
import net.zomis.games.impl.alchemists.AlchemistsDelegationGame
import net.zomis.games.impl.cards.Grizzled
import net.zomis.games.impl.grids.Battleship
import net.zomis.games.impl.grids.KingDomino
import net.zomis.games.impl.minesweeper.Flags
import net.zomis.games.impl.paths.Backgammon
import net.zomis.games.impl.ttt.*
import net.zomis.games.impl.words.Decrypto
import net.zomis.games.impl.words.Wordle

object ServerGames {

    val beta = listOf(
        Pentago.game,
        AlchemistsDelegationGame.game,
        Battleship.game,
        Decrypto.game,
        Red7.Game.game,
        LightsOut.Game.game,
    )

    val games = listOf(
        Flags.game,
        NoThanks.game,
        Grizzled.game,
        Wordle.game,
        Backgammon.game,
        KingDomino.game,
        TTTUpgrade.game,
        SpiceRoadDsl.game,
        Dixit.game,
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
    ).plus(beta).associateBy { it.name }

    fun setup(gameType: String): GameSetupImpl<Any>? {
        val spec = games[gameType] as GameSpec<Any>? ?: return null
        return GameSetupImpl(spec)
    }

    fun entrypoint(gameType: String): GameEntryPoint<Any>? {
        val spec = games[gameType] as GameSpec<Any>? ?: return null
        return GameEntryPoint(spec)
    }

}
