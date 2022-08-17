package net.zomis.games.ecs

import kotlinx.coroutines.test.runTest
import net.zomis.games.WinResult
import net.zomis.games.api.GamesApi
import net.zomis.games.components.grids.mnkLines
import net.zomis.games.dsl.GamesImpl
import net.zomis.games.dsl.listeners.BlockingGameListener
import net.zomis.games.listeners.LimitedNextViews
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class ECSGameTest {

    val factory = GamesApi.gameECS()
    val m = factory.config("m") { 0 }
    val n = factory.config("n") { 0 }
    val k = factory.config("k") { 0 }
    val play = factory.simpleAction("play")
    val activePlayer = factory.components.playerIndex().new("activePlayer")

    val ttt = factory.game("TTT") {
        playersFixed(2)
        defaultConfigs(listOf(m to 3, n to 3, k to 3))
        root {
            game has activePlayer.value(0)
            game has grid(config(m), config(n)) {
                tile mayHave playerIndex()
                tile has action(play)
            }
            game has actionRule(play) {
                val (entity, root) = (entity to root)
                precondition { playerIndex == root[activePlayer] }
                requires { entity.doesNotHave(PlayerIndex) }
                perform { entity[PlayerIndex] = playerIndex }
                perform {
                    val winners = root[Grid].mnkLines(includeDiagonals = true).map { line ->
                        line.hasConsecutive(config(k)) { it.getOrNull(PlayerIndex) }
                    }.filterNotNull().toList()
                    if (winners.isNotEmpty()) {
                        eliminations.eliminateMany(winners, WinResult.WIN)
                        eliminations.eliminateRemaining(WinResult.LOSS)
                    }
                }
                perform { root.component(activePlayer).nextPlayer() }
            }
        }
    }
    val connect4 = factory.game("Connect4") {
        // See https://en.wikipedia.org/wiki/Connect_Four for other fun variants
        copyFrom(ttt)
        defaultConfigs(listOf(m to 7, n to 6, k to 4))
        root {
            game has actionRule(play) {
                val (entity, root) = (entity to root)
                requires {
                    root[Grid].getOrNull(entity[Point].x, entity[Point].y + 1)?.has(PlayerIndex) ?: true
                }
            }
        }
        // start from TTT and just edit it a bit, changing default config and which moves are allowed
    }
    val tttUpgrade = factory.game("TTTUpgrade") {
        copyFrom(ttt)
        // start from TTT but modify the gridd tiles to also have a size
        // and make each player have an inventory of sizes (2 of each)
    }
    val quixo = factory.game("Quixo") {
        // start from TTT, modify size to 5x5, prevent play action and add move action.
        // so essentially... make a whole different game? Don't see what TTT brings to the table here.
    }
    val uttt = factory.game("UTTT") {
        defaultConfigs(listOf(m to 3, n to 3, k to 3))
        root {
            game has actionRule(play) {

            }
            game has grid(m.value, n.value) {
                // include(ttt)
            }
        }
    }

    @Test
    fun play() = runTest {
        val blocking = BlockingGameListener()
        val game = GamesImpl.game(ttt.toDsl()).setup().startGame(this, 2) {
            listOf(blocking, LimitedNextViews(10)) // + SanityCheckListener(it)
        }
        blocking.await()
        Assertions.assertEquals(9, game.actions.actionTypes.size)
        blocking.awaitAndPerform(0, "/grid/0,2/play", Unit)
        blocking.await()
        Assertions.assertEquals(0, game.actions.allAvailableActions(0, null).count())
        Assertions.assertEquals(8, game.actions.types().sumOf { it.availableActions(1, null).count() })
        blocking.awaitAndPerform(0, "/grid/1,1/play", Unit) // IllegalAction (wrong playerIndex)
        blocking.await()
        Assertions.assertEquals(8, game.actions.types().sumOf { it.availableActions(1, null).count() })
        blocking.awaitAndPerform(1, "/grid/1,1/play", Unit)
        blocking.await()
        Assertions.assertEquals(7, game.actions.types().sumOf { it.availableActions(0, null).count() })

        val view = game.view(0)
        val empty = mapOf("playerIndex" to null, "actions" to mapOf("play" to true))
        val player0 = mapOf("playerIndex" to 0, "actions" to mapOf("play" to false))
        val player1 = mapOf("playerIndex" to 1, "actions" to mapOf("play" to false))
        Assertions.assertEquals(
            mapOf(
                "activePlayer" to 0,
                "grid" to mapOf(
                    "left" to 0,
                    "top" to 0,
                    "width" to 3,
                    "height" to 3,
                    "grid" to listOf(listOf(empty, empty, empty), listOf(empty, player1, empty), listOf(player0, empty, empty))
                ),
            ),
            view
        )
        blocking.awaitAndPerform(0, "/grid/0,0/play", Unit)
        blocking.awaitAndPerform(1, "/grid/2,1/play", Unit)
        blocking.awaitAndPerform(0, "/grid/0,1/play", Unit)
        blocking.await()
        Assertions.assertTrue(game.isGameOver())
    }

    @Test
    fun connect4() = runTest {
        val blocking = BlockingGameListener()
        val game = GamesImpl.game(connect4.toDsl()).setup().startGame(this, 2) {
            listOf(blocking)
        }
        blocking.await()
        Assertions.assertEquals(42, game.actions.actionTypes.size)
        Assertions.assertEquals(7, game.actions.allAvailableActions(0).count())
        Assertions.assertEquals(0, game.actions.allAvailableActions(1).count())
        blocking.awaitAndPerform(0, "/grid/3,5/play", Unit)
        blocking.await()
        println(game.view(0))
        Assertions.assertEquals(0, game.actions.allAvailableActions(0).count())
        Assertions.assertEquals(7, game.actions.allAvailableActions(1).count())
        blocking.awaitAndPerform(1, "/grid/3,4/play", Unit)
        blocking.await()
        Assertions.assertEquals(7, game.actions.allAvailableActions(0).count())
        game.stop()
    }

    @Test
    fun uttt() {
        // Implement UTTT as ECS.
    }

    @Test
    fun tripleTTT() {
        // 3 TTT games
    }



}