package net.zomis.games.server2

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import klog.KLoggers
import net.zomis.core.events.EventSystem
import net.zomis.games.common.Point
import net.zomis.games.server2.clients.WSClient
import net.zomis.games.server2.clients.getInt
import net.zomis.games.server2.clients.getText
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.URI
import java.util.UUID

class DslGameTest {

    private val logger = KLoggers.logger(this)

    var server: Server2? = null
    val config = testServerConfig()
    val dslGame = "DSL-TTT"

    @BeforeEach
    fun startServer() {
        server = Server2(EventSystem())
        server!!.start(config)

        val tokens = mutableListOf("guest-12345", "guest-23456")
        fun authTest(message : ClientJsonMessage) {
            AuthorizationSystem(server!!.events).handleGuest(message.client, tokens.removeAt(0), UUID.randomUUID()) {""}
        }
        server!!.messageRouter.handler("auth/guest", ::authTest)
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

        p1.send("""{ "route": "auth/guest" }""")
        p1.expectJsonObject { it.getText("type") == "Auth" }.get("playerId").asText()
        p2.send("""{ "route": "auth/guest" }""")
        p2.expectJsonObject { it.getText("type") == "Auth" }.get("playerId").asText()

        p1.send("""{ "route": "lobby/join", "gameTypes": ["$dslGame"], "maxGames": 1 }""")
        Thread.sleep(100)
        p2.send("""{ "route": "lobby/join", "gameTypes": ["$dslGame"], "maxGames": 1 }""")

        p1.send("""{ "game": "$dslGame", "type": "matchMake" }""")
        Thread.sleep(100)
        p2.send("""{ "game": "$dslGame", "type": "matchMake" }""")
        p1.expectJsonObject { it.getText("type") == "LobbyChange" }
        p1.expectJsonObject {
            it.getText("type") == "GameStarted" && it.getText("gameType") == dslGame &&
                    it.get("access").get("0").asText() == "ADMIN"
        }
        p2.expectJsonObject {
            it.getText("type") == "GameStarted" && it.getText("gameType") == dslGame &&
                    it.get("access").get("1").asText() == "ADMIN"
        }
        p1.expectJsonObject { it.getText("type") == "UpdateView" }
        p2.expectJsonObject { it.getText("type") == "UpdateView" }

        p1.sendAndExpectResponse("""{ "route": "games/$dslGame/1/view", "playerIndex": 0 }""")
        val viewResponse = p1.expectJsonObject { it.getText("type") == "GameView" }
        Assertions.assertEquals(3, viewResponse["view"]["board"]["grid"].size())
        Assertions.assertEquals(3, viewResponse["view"]["board"]["grid"][0].size())
        Assertions.assertEquals(3, viewResponse["view"]["board"]["grid"][1].size())
        Assertions.assertEquals(3, viewResponse["view"]["board"]["grid"][2].size())
        p2.sendAndExpectResponse("""{ "route": "games/$dslGame/1/view", "playerIndex": 1 }""")
        p2.expectJsonObject { it.getText("type") == "GameView" }

        // Try to cheat - wrong player
        p2.sendAndExpectResponse("""{ "route": "games/$dslGame/1/move", "moveType": "play", "move": { "x": 0, "y": 2 }, "playerIndex": 1 }""")
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
        p1.sendAndExpectResponse("""{ "route": "games/$dslGame/1/move", "moveType": "play", "move": { "x": 1, "y": 2 }, "playerIndex": 0 }""")
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
            val playerIndex = index % 2
            val cl = if (playerIndex == 0) p1 else p2
            val move = mapper.writeValueAsString(moveParameter)
            cl.send("""{ "route": "games/$dslGame/1/move", "moveType": "$moveType", "move": $move, "playerIndex": $playerIndex }""")
            p1.expectJsonObject { true }
            p2.expectJsonObject { true }
        }
    }

}
