package net.zomis.games.server2

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import klog.KLoggers
import net.zomis.core.events.EventSystem
import net.zomis.games.dsl.Point
import net.zomis.games.server2.clients.ur.WSClient
import net.zomis.games.server2.clients.ur.getInt
import net.zomis.games.server2.clients.ur.getText
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.URI

class DslGameTest {

    private val logger = KLoggers.logger(this)

    var server: Server2? = null
    val config = testServerConfig()
    val dslGame = "DSL-TTT"

    @BeforeEach
    fun startServer() {
        server = Server2(EventSystem())
        server!!.start(config)
    }

    @AfterEach
    fun stopServer() {
        server!!.stop()
    }

    @Test
    fun dsl() {
        val p1 = WSClient(URI("ws://127.0.0.1:${config.webSocketPort}/websocket"))
        p1.connectBlocking()

        val p2 = WSClient(URI("ws://127.0.0.1:${config.webSocketPort}/websocket"))
        p2.connectBlocking()

        p1.send("""{ "type": "ClientGames", "gameTypes": ["$dslGame"], "maxGames": 1 }""")
        Thread.sleep(100)
        p2.send("""{ "type": "ClientGames", "gameTypes": ["$dslGame"], "maxGames": 1 }""")

        p1.send("""{ "game": "$dslGame", "type": "matchMake" }""")
        Thread.sleep(100)
        p2.send("""{ "game": "$dslGame", "type": "matchMake" }""")
        p1.expectJsonObject { it.getText("type") == "LobbyChange" }
        p1.expectJsonObject {
            it.getText("type") == "GameStarted" && it.getText("gameType") == dslGame &&
                    it.getInt("yourIndex") == 0
        }
        p2.expectJsonObject {
            it.getText("type") == "GameStarted" && it.getText("gameType") == dslGame &&
                    it.getInt("yourIndex") == 1
        }

        p1.sendAndExpectResponse("""{ "gameType": "$dslGame", "gameId": "1", "type": "ViewRequest" }""")
        p1.expectJsonObject { it.getText("type") == "GameView" }
        p2.sendAndExpectResponse("""{ "gameType": "$dslGame", "gameId": "1", "type": "ViewRequest" }""")
        p2.expectJsonObject { it.getText("type") == "GameView" }

        // Try to cheat - wrong player
        p2.sendAndExpectResponse("""{ "gameType": "$dslGame", "gameId": "1", "moveType": "play", "type": "move", "move": { "x": 0, "y": 2 } }""")
        p2.takeUntilJson { it.getText("type") == "IllegalMove" }

        sendAndExpect("play", p1, p2, listOf(
            Point(1, 1),
            Point(0, 1),
            Point(2, 2),
            Point(0, 0),
            Point(0, 2),
            Point(1, 0)
        ))

        // Win the game
        p1.sendAndExpectResponse("""{ "gameType": "$dslGame", "gameId": "1", "moveType": "play", "type": "move", "move": { "x": 1, "y": 2 } }""")
        var obj = p1.takeUntilJson { it.getText("type") == "PlayerEliminated" }
        assert(obj.getText("gameType") == dslGame)
        assert(obj.getInt("player") == 0)
        assert(obj.get("winner").asBoolean())
        assert(obj.getInt("position") == 1)

        obj = p1.expectJsonObject { it.getText("type") == "PlayerEliminated" }
        assert(obj.getText("gameType") == dslGame)
        assert(obj.getInt("player") == 1)
        assert(!obj.get("winner").asBoolean())
        assert(obj.getInt("position") == 2)

        obj = p2.takeUntilJson { it.getText("type") == "PlayerEliminated" }
        assert(obj.getText("gameType") == dslGame)
        assert(obj.getInt("player") == 0)
        assert(obj.get("winner").asBoolean())
        assert(obj.getInt("position") == 1)

        obj = p2.expectJsonObject { it.getText("type") == "PlayerEliminated" }
        assert(obj.getText("gameType") == dslGame)
        assert(obj.getInt("player") == 1)
        assert(!obj.get("winner").asBoolean())
        assert(obj.getInt("position") == 2)

        p1.close()
        p2.close()
    }

    private fun sendAndExpect(moveType: String, p1: WSClient, p2: WSClient, pairs: List<Any>) {
        val mapper = jacksonObjectMapper()
        pairs.forEachIndexed { index, moveParameter ->
            val cl = if (index % 2 == 0) p1 else p2
            val move = mapper.writeValueAsString(moveParameter)
            cl.send("""{ "gameType": "$dslGame", "gameId": "1", "moveType": "$moveType", "type": "move", "move": $move }""")
            p1.expectJsonObject { true }
            p2.expectJsonObject { true }
        }
    }

}
