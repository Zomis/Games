package net.zomis.games.server2

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import klog.KLoggers
import net.zomis.core.events.EventSystem
import net.zomis.games.dsl.Point
import net.zomis.games.dsl.impl.GameImpl
import net.zomis.games.server2.ais.ServerAIs
import net.zomis.games.server2.clients.ur.WSClient
import net.zomis.games.server2.clients.ur.getInt
import net.zomis.games.server2.clients.ur.getText
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.net.URI

class DslRandomPlayTest {

    private val logger = KLoggers.logger(this)

    var server: Server2? = null
    val config = testServerConfig()

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
    @ParameterizedTest(name = "Random play {0}")
    @ValueSource(strings = ["TTT", "UTTT", "Connect4"])
    fun dsl(gameName: String) {
        val dslGame = "DSL-$gameName"
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
        val players = arrayOf(p1, p2)

        // Find game
        val game = server!!.gameSystem.getGameType(dslGame)!!.runningGames["1"]!!
        val gameImpl = game.obj as GameImpl<*>
        val playerRange = 0 until game.players.size

        while (!gameImpl.isGameOver()) {
            val actions = playerRange.mapNotNull {playerIndex ->
                ServerAIs().randomAction(game, playerIndex).firstOrNull()
            }
            val request = actions.first()
            val playerSocket = players[request.player]
            val moveString = jacksonObjectMapper().writeValueAsString(request.move)
            playerSocket.send("""{ "gameType": "$dslGame", "gameId": "1", "moveType": "${request.moveType}", "type": "move", "move": $moveString }""")
            p1.expectJsonObject { true }
            p2.expectJsonObject { true }
        }

/*        // Game is finished
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
*/
        p1.close()
        p2.close()
    }

}
