package net.zomis.games.server2

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import klog.KLoggers
import net.zomis.core.events.EventSystem
import net.zomis.games.dsl.impl.GameImpl
import net.zomis.games.server2.ais.ServerAIs
import net.zomis.games.server2.clients.WSClient
import net.zomis.games.server2.clients.getInt
import net.zomis.games.server2.clients.getText
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
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

    companion object {
        @JvmStatic
        fun serverGames(): List<Arguments> {
            return ServerGames.games.keys.sorted().map { Arguments.of(it) }
        }
    }

    @ParameterizedTest(name = "Random play {0}")
    @MethodSource("serverGames")
    fun dsl(dslGame: String) {
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
        var actionCounter = 0

        while (!gameImpl.isGameOver()) {
            actionCounter++
            if (actionCounter % 10 == 0) {
                p1.sendAndExpectResponse("""{ "gameType": "$dslGame", "gameId": "1", "type": "ViewRequest" }""")
                p1.expectJsonObject { it.getText("type") == "GameView" }
            }
            val actions = playerRange.mapNotNull {playerIndex ->
                ServerAIs(emptySet()).randomAction(game, playerIndex).firstOrNull()
            }
            if (actions.isEmpty()) {
                p1.sendAndExpectResponse("""{ "gameType": "$dslGame", "gameId": "1", "type": "ViewRequest" }""")
                p1.expectJsonObject { it.getText("type") == "GameView" }
                throw IllegalStateException("Game is not over but no actions available. Is the game a draw?")
            }
            val request = actions.first()
            val playerSocket = players[request.player]
            val moveString = jacksonObjectMapper().writeValueAsString(request.move)
            playerSocket.send("""{ "gameType": "$dslGame", "gameId": "1", "moveType": "${request.moveType}", "type": "move", "move": $moveString }""")
            p1.expectJsonObject { true }
            p2.expectJsonObject { true }
        }

        // Game is finished
        var obj = p1.takeUntilJson { it.getText("type") == "PlayerEliminated" }
        assert(obj.getText("gameType") == dslGame)
        assert(obj.get("winner").isBoolean)
//        assert(obj.getInt("player") == 0)
//        assert(obj.getInt("position") == 1)

        obj = p1.expectJsonObject { it.getText("type") == "PlayerEliminated" }
        assert(obj.getText("gameType") == dslGame)
        assert(obj.get("winner").isBoolean)

        obj = p2.takeUntilJson { it.getText("type") == "PlayerEliminated" }
        assert(obj.getText("gameType") == dslGame)
        assert(obj.get("winner").isBoolean)

        obj = p2.expectJsonObject { it.getText("type") == "PlayerEliminated" }
        assert(obj.getText("gameType") == dslGame)
        assert(obj.get("winner").isBoolean)

        p1.expectJsonObject { it.getText("type") == "GameEnded" }
        p2.expectJsonObject { it.getText("type") == "GameEnded" }

        p1.sendAndExpectResponse("""{ "gameType": "$dslGame", "gameId": "1", "type": "ViewRequest" }""")
        p1.expectJsonObject { it.getText("type") == "GameView" }
        p2.sendAndExpectResponse("""{ "gameType": "$dslGame", "gameId": "1", "type": "ViewRequest" }""")
        obj = p2.expectJsonObject { it.getText("type") == "GameView" }
        val winner = obj["view"]["winner"]
        println("Winner is $winner")
        assert(winner.isInt) { "Winner is not an int" }

        p1.close()
        p2.close()
    }

}
