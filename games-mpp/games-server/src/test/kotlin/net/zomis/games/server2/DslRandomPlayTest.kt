package net.zomis.games.server2

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import klog.KLoggers
import kotlinx.coroutines.runBlocking
import net.zomis.core.events.EventSystem
import net.zomis.games.dsl.ConsoleView
import net.zomis.games.dsl.GameEntryPoint
import net.zomis.games.dsl.games.AllGamesTest
import net.zomis.games.server2.ais.ServerAIs
import net.zomis.games.server2.ais.serialize
import net.zomis.games.server2.clients.WSClient
import net.zomis.games.server2.clients.getText
import net.zomis.games.server2.games.PlayerGameMoveRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.lang.RuntimeException
import java.net.URI
import java.util.UUID

class DslRandomPlayTest {

    private val logger = KLoggers.logger(this)

    var server: Server2? = null
    val config = testServerConfig()

    @BeforeEach
    fun startServer() {
        server = Server2(EventSystem())
        server!!.start(config)

        val tokens = generateSequence(1) { it + 1 }.map { "guest-$it" }.iterator()
        fun authTest(message : ClientJsonMessage) {
            val nextToken = tokens.next()
            AuthorizationSystem(server!!.events).handleGuest(message.client, nextToken, UUID.randomUUID()) {""}
        }
        server!!.messageRouter.handler("auth/guest", ::authTest)
    }

    @AfterEach
    fun stopServer() {
        logger.info { "Stopping server from test" }
        server!!.stop()
    }

    companion object {
        val playingMap = AllGamesTest().playingMap

        @JvmStatic
        fun serverGames(): List<Arguments> = AllGamesTest.gameList()
    }

    @ParameterizedTest(name = "Random play {0} with {1} players")
    @MethodSource("serverGames")
    @Disabled
    fun dsl(gameType: GameEntryPoint<Any>, playerCount: Int) {
        val dslGame = gameType.gameType

        val clients = (0 until playerCount).map { WSClient(URI("ws://127.0.0.1:${config.port}/websocket")) }
        clients.forEach { it.connectBlocking() }

        val clientsById = clients.associateBy {client ->
            client.send("""{ "route": "auth/guest" }""")
            client.expectJsonObject { it.getText("type") == "Auth" }.get("playerId").asText()
        }

        clients.forEach { client ->
            client.send("""{ "route": "lobby/join", "gameTypes": ["$dslGame"], "maxGames": 1 }""")
            Thread.sleep(100)
        }
        clients.forEachIndexed { index, client ->
            repeat(clients.size - 1 - index) {
                client.expectJsonObject { it.getText("type") == "LobbyChange" }
            }
        }

        clients[0].send("""{ "route": "invites/prepare", "gameType": "$dslGame" }""")
        val config = clients[0].expectJsonObject { it.getText("type") == "InvitePrepare" }["config"]
        val mapper = jacksonObjectMapper()
        val configString = mapper.writeValueAsString(config)

        clients[0].send("""{ "route": "invites/start", "gameType": "$dslGame", "options": {}, "gameOptions": $configString }""")
        val inviteId = clients[0].expectJsonObject { it.getText("type") == "InviteView" }.getText("inviteId")
        val remainingPlayers = clientsById.filter { it.value != clients[0] }

        val invitees = mapper.writeValueAsString(remainingPlayers.keys)
        if (remainingPlayers.isNotEmpty()) {
            clients[0].sendAndExpectResponse("""{ "route": "invites/$inviteId/send", "gameType": "$dslGame", "invite": $invitees }""")

            clients.subList(1, clients.size).forEach { client ->
                client.send("""{ "route": "invites/$inviteId/respond", "accepted": true }""")
                client.takeUntilJson { it.getText("type") == "InviteView" }
            }
            clients[0].takeUntilJson {
                it.getText("type") == "InviteView" && it["players"].size() == clients.size
            }
        }
        if (playerCount < gameType.setup().playersCount.maxOrNull()!! || playerCount == 1) {
            // Only start game manually if needed. If it's maximum players, game will autostart
            clients[0].send("""{ "route": "invites/$inviteId/start" }""")
        }

        clients.forEach {client ->
            println("Client $client take until GameStarted")
            client.takeUntilJson { it.getText("type") == "GameStarted" }
        }

        // Find game
        println("Finding game")
        val game = server!!.gameSystem.getGameType(dslGame)!!.runningGames["1"]!!
        repeat(5) {
            if (game.obj == null) {
                logger.warn { "Game object was null, waiting..." }
                Thread.sleep(1000)
            }
        }
        val gameImpl = game.obj ?: throw IllegalStateException("Game was not started correctly: ${gameType.gameType} with $playerCount players")
        val players = game.playerList().mapIndexed { index, client ->
            index to clientsById.getValue(client.playerId)
        }
        val playerIdsByIndex = game.playerList().withIndex().associate { it.index to it.value.playerId }
        Assertions.assertTrue(game.players.all { it.value.access.isNotEmpty() }) { "Player access is not correct: ${game.players}" }

        logger.info { "clientsById: $clientsById" }
        logger.info { "Server Players: ${game.players.map { it.key.playerId to it.value.access }}" }
        logger.info { "Player List: ${game.playerList().map { it.playerId }}" }

//        if (game.playerList().map { it.playerId } != playerIds) throw IllegalStateException("Mismatching lists")

        playerIdsByIndex.forEach { (playerIndex, playerId) ->
            val client = clientsById.getValue(playerId)
            client.sendAndExpectResponse("""{ "route": "games/$dslGame/1/view", "playerIndex": $playerIndex }""")
            client.takeUntilJson { it.getText("type") == "GameView" }
        }

        var actionCounter = 0

        while (!gameImpl.isGameOver()) {
            if (actionCounter > 10000) {
                throw RuntimeException("Game seems to be stuck. Not finishing after $actionCounter moves. Last view is ${gameImpl.view(0)}")
            }
            actionCounter++
            if (actionCounter % 10 == 0) {
                clients[0].sendAndExpectResponse("""{ "route": "games/$dslGame/1/view", "playerIndex": 0 }""")
                clients[0].takeUntilJson { it.getText("type") == "GameView" }
            }
            val actions: List<PlayerGameMoveRequest> = players.mapNotNull {playerClient ->
                val playerIndex = playerClient.first
                val moveHandler = playingMap[gameImpl.gameType]
                if (moveHandler != null) {
                    val ai = gameType.setup().findAI(moveHandler)
                        ?: throw IllegalArgumentException("Missing AI: $moveHandler for game ${gameImpl.gameType}")
                    runBlocking {
                        ai.simpleAction(gameImpl, playerIndex)?.let {
                            val serialized = gameImpl.actions.type(it.actionType)!!.actionType.serialize(it.parameter)
                            PlayerGameMoveRequest(game.highestAccessTo(playerIndex)!!, game, playerIndex, it.actionType, serialized, true)
                        }
                    }
                } else {
                    ServerAIs.randomAction(game, game.highestAccessTo(playerIndex)!!, playerIndex)
                }
            }.map { it.serialize(gameImpl) }
            if (actions.isEmpty()) {
                clients[0].sendAndExpectResponse("""{ "route": "games/$dslGame/1/view", "playerIndex": 0 }""")
                val view = clients[0].takeUntilJson { it.getText("type") == "GameView" }
                ConsoleView<Any>().showView(gameImpl)
                throw IllegalStateException("Game is not over but no actions available after $actionCounter actions. Is the game a draw? View is $view")
            }
            val request = actions.random() // If multiple players wants to perform an action, just do one of them
            logger.info { "Chosen action: $request out of the ${actions.size} player actions: $actions" }
            val playerSocket = clientsById.getValue(request.client.playerId.toString())
            val moveString = jacksonObjectMapper().writeValueAsString(request.move)
            playerSocket.send("""{ "route": "games/$dslGame/1/move", "playerIndex": ${request.player}, "moveType": "${request.moveType}", "move": $moveString }""")
            clients.forEach {client ->
                client.takeUntilJson { it.getTextOrDefault("type", "") == "GameMove" }
            }
        }
        logger.info { "Game is finished" }

        // Game is finished
        clients.forEach {client ->
            /*
            repeat(clients.size) {
                val obj = client.takeUntilJson { it.getText("type") == "PlayerEliminated" }
                assert(obj.getText("gameType") == dslGame)
                assert(obj.get("winner").isBoolean)
            }
            */
            client.takeUntilJson { it.getText("type") == "GameEnded" }
        }

        val eliminations = gameImpl.eliminations.eliminations()
        assert(eliminations.size == clients.size)

        clients.forEach { it.close() }
    }

}
