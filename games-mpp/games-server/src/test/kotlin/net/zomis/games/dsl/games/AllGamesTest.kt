package net.zomis.games.dsl.games

import kotlinx.coroutines.test.runTest
import net.zomis.games.dsl.GameEntryPoint
import net.zomis.games.dsl.GamesImpl
import net.zomis.games.dsl.ReplayException
import net.zomis.games.dsl.impl.GameAI
import net.zomis.games.dsl.impl.GameAIs
import net.zomis.games.dsl.listeners.BlockingGameListener
import net.zomis.games.impl.DslSplendor
import net.zomis.games.impl.SetGame
import net.zomis.games.impl.SpiceRoadDsl
import net.zomis.games.impl.cards.Grizzled
import net.zomis.games.impl.words.Decrypto
import net.zomis.games.listeners.*
import net.zomis.games.server2.ServerGames
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path

class AllGamesTest {

    companion object {
        @JvmStatic
        fun gameList(): List<Arguments> {
            return ServerGames.games.values.filter { !ServerGames.beta.contains(it) }.map {
                val entryPoint = GamesImpl.game(it)
                val playerCount = entryPoint.setup().playersCount
                val randomCount = playerCount.random()

                Arguments.of(entryPoint, randomCount)
            }
        }
    }

    val playingMap = mapOf(
        // Wordle?
        Grizzled.game.name to "#AI_Chicken",
        SpiceRoadDsl.game.name to "#AI_BuyFirst",
        Decrypto.game.name to "#AI_NoChat",
        SetGame.game.name to "#AI_SetCheat50",
        DslSplendor.splendorGame.name to "#AI_BuyFirst"
    )

    @ParameterizedTest(name = "Run tests for {0} with {1} players")
    @MethodSource("gameList")
    fun gameTests(gameType: GameEntryPoint<Any>, playerCount: Int) = runTest {
        gameType.runTests()
    }

    @ParameterizedTest(name = "Sanity Check {0} with {1} players")
    @MethodSource("gameList")
    fun sanityCheck(gameType: GameEntryPoint<Any>, playerCount: Int) = runTest {
        val ai = playingMap[gameType.gameType]?.let { gameType.setup().findAI(it) } ?: GameAI("#AI_Random") {
            action {
                GameAIs.randomActionable(game, playerIndex)
            }
        }
        val awaiting = BlockingGameListener()
        val replay = ReplayListener(gameType.gameType)
        try {
            val game = gameType.setup().startGame(this, playerCount) { game ->
                val aiListeners = game.playerIndices.map {
                    ai.gameListener(game, it)
                }
                listOf(
                    LimitedNextViews(10),
                    replay,
                    SanityCheckListener(game),
                    MaxMoves(10000)
                ) + aiListeners + awaiting
            }
            awaiting.awaitGameEnd()
            Assertions.assertTrue(game.isGameOver())
        } catch (ex: Exception) {
            val filename = "failed-game-${gameType.gameType}-${System.currentTimeMillis()}.json"
            FileReplay(Path.of(filename), replay).save()
            throw ReplayException("Sanity check failed for game ${gameType.gameType}. Saved replay to $filename", ex)
        }
    }

}