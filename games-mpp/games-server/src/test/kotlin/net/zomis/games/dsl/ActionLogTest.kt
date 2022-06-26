package net.zomis.games.dsl

import kotlinx.coroutines.test.runTest
import net.zomis.games.WinResult
import net.zomis.games.dsl.games.action
import net.zomis.games.dsl.impl.*
import net.zomis.games.listeners.BlockingGameListener
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import kotlin.math.sign

class ActionLogTest {

    data class LogTestGame(var value: Int)
    private val factory = GameCreator(LogTestGame::class)
    private val change = factory.action("change", Int::class)
    private val spec = factory.game("LogTest") {
        setup {
            init { LogTestGame(0) }
        }
        actionRules {
            action(change) {
                options { -3..3 }
                effect {
                    game.value += action.parameter
                    logSecret(action.playerIndex) { "$player changed the value with ${obj(action)} to ${viewLink("something else", "number", game.value)}" }
                        .publicLog { "$player changed the value" }
                    /*
                    logEntry: {
                        player: 0
                        parts: [
                            { type: player, value: 0 },
                            { type: text, value: " changed the value with " },
                            { type: highlight, value: 2 },
                            { type: text, value: " to " },
                            { type: link, text: "something else", viewType: "number", value: game.value },
                        ],
                        highlights: [2]
                    }
                    */
                    log { "$player changed the value in direction ${action.sign}" }
                    if (game.value == 0) {
                        eliminations.eliminateRemaining(WinResult.WIN)
                    }
                }
            }
        }
    }

    @Test
    fun test() = runTest {
        val entry = GamesImpl.game(spec)
        val blockingGame = BlockingGameListener()
        val game = entry.setup().startGame(this, 2) {
            listOf(blockingGame)
        }
        blockingGame.awaitAndPerform(0, change, 2)
        blockingGame.awaitAndPerform(0, change, 3)
        blockingGame.awaitAndPerform(1, change, -2)
        blockingGame.await()

        val logs = game.stateKeeper.logs()
        Assertions.assertEquals(2, logs.size)
        Assertions.assertEquals(LogPartPlayer(1), logs[0].secret!!.parts[0])
        Assertions.assertTrue(logs[0].secret!!.private)
        Assertions.assertEquals(LogPartText(" changed the value with "), logs[0].secret!!.parts[1])
        Assertions.assertEquals(LogPartHighlight(-2), logs[0].secret!!.parts[2])
        Assertions.assertEquals(LogPartText(" to "), logs[0].secret!!.parts[3])
        Assertions.assertEquals(LogPartLink("something else", "number", 3), logs[0].secret!!.parts[4])

        Assertions.assertFalse(logs[0].public!!.private)
        Assertions.assertEquals(LogPartPlayer(1), logs[0].public!!.parts[0])
        Assertions.assertEquals(LogPartText(" changed the value"), logs[0].public!!.parts[1])

        Assertions.assertFalse(logs[1].public!!.private)
        Assertions.assertEquals(LogPartPlayer(1), logs[1].public!!.parts[0])
        Assertions.assertEquals(LogPartText(" changed the value in direction -1"), logs[1].public!!.parts[1])

        blockingGame.awaitAndPerform(1, change, -2)
        blockingGame.awaitAndPerform(0, change, 1)
        blockingGame.awaitAndPerform(1, change, -2)
        blockingGame.await()

        Assertions.assertTrue(game.isGameOver())
        println("assertions done")
    }

}