package net.zomis.games.dsl

import net.zomis.games.WinResult
import net.zomis.games.dsl.flow.runBlocking
import net.zomis.games.dsl.impl.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import kotlin.math.sign

class ActionLogTest {

    data class LogTestGame(var value: Int)
    val factory = GameCreator(LogTestGame::class)
    val change = factory.action("change", Int::class)
    val spec = factory.game("LogTest") {
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
                        playerEliminations.eliminateRemaining(WinResult.WIN)
                    }
                }
            }
        }
    }

    @Test
    fun test() {
        val entry = GamesImpl.game(spec)
        val play = entry.replayable(2, null, entry.inMemoryReplay()).runBlocking()
        play.action(0, change, 2)
        play.action(0, change, 3)
        play.action(1, change, -2)
        val game = play.game
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

        play.action(1, change,-2)
        play.action(0, change,1)
        play.action(1, change,-2)
        Assertions.assertTrue(game.isGameOver())
    }

}