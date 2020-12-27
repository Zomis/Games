package net.zomis.games.server2

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import klog.KLoggers
import net.zomis.core.events.EventSystem
import net.zomis.games.dsl.Actionable
import net.zomis.games.dsl.GameEntryPoint
import net.zomis.games.dsl.GamesImpl
import net.zomis.games.impl.SplendorGame
import net.zomis.games.dsl.impl.GameController
import net.zomis.games.dsl.impl.GameControllerContext
import net.zomis.games.dsl.impl.GameControllerScope
import net.zomis.games.dsl.impl.GameImpl
import net.zomis.games.impl.SetAction
import net.zomis.games.impl.SetGame
import net.zomis.games.impl.SetGameModel
import net.zomis.games.impl.words.Decrypto
import net.zomis.games.server2.ais.AIRepository
import net.zomis.games.server2.ais.ServerAIs
import net.zomis.games.server2.ais.gamescorers.DecryptoScorers
import net.zomis.games.server2.ais.gamescorers.SplendorScorers
import net.zomis.games.server2.ais.serialize
import net.zomis.games.server2.clients.WSClient
import net.zomis.games.server2.clients.getText
import net.zomis.games.server2.games.PlayerGameMoveRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.lang.RuntimeException
import java.net.URI
import java.util.UUID
import kotlin.random.Random
import kotlin.reflect.KClass

class DslRandomPlayTest {

    private val logger = KLoggers.logger(this)

    var server: Server2? = null
    val config = testServerConfig()
    val serverAIs = ServerAIs(AIRepository(), emptySet())
    val random = Random.Default

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
        @JvmStatic
        fun serverGames(): List<Arguments> {
            return ServerGames.games.values.sortedBy { it.name }
            .map {
                val entryPoint = GamesImpl.game(it)
                val playerCount = entryPoint.setup().playersCount
                val randomCount = playerCount.random()

                Arguments.of(entryPoint, randomCount)
            }
        }
    }

    fun randomSetMove(context: GameControllerScope<SetGameModel>): Actionable<SetGameModel, Any>? {
        return if (random.nextBoolean()) {
            context.model.findSets(context.model.board.cards).firstOrNull()?.let {
                context.game.actions[SetGame.callSet.name]!!.createAction(context.playerIndex, SetAction(it.map { c -> c.toStateString() }))
            }!!
        } else {
            serverAIs.randomActionable(context.game, context.playerIndex)
        }
    }

    val playingMap = mapOf<KClass<*>, GameController<*>>(
        Decrypto.Model::class to { ctx -> DecryptoScorers.noChat.createController().invoke(ctx as GameControllerScope<Decrypto.Model>) },
        SetGameModel::class to { context: GameControllerScope<*> -> randomSetMove(context as GameControllerScope<SetGameModel>) },
        SplendorGame::class to { ctx -> SplendorScorers.aiBuyFirst.createController().invoke(ctx as GameControllerScope<SplendorGame>) }
    )

    @ParameterizedTest(name = "Random play {0} with {1} players")
    @MethodSource("serverGames")
    fun dsl(gameType: GameEntryPoint<Any>, playerCount: Int) {
        val dslGame = gameType.gameType
        gameType.runTests()

        val clients = (1..playerCount).map { WSClient(URI("ws://127.0.0.1:${config.webSocketPort}/websocket")) }
        clients.forEach { it.connectBlocking() }

        val playerIds = clients.map {client ->
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
        val remainingPlayers = playerIds.subList(1, playerIds.size)

        val invitees = mapper.writeValueAsString(remainingPlayers)
        clients[0].sendAndExpectResponse("""{ "route": "invites/$inviteId/send", "gameType": "$dslGame", "invite": $invitees }""")

        clients.subList(1, clients.size).forEach { client ->
            client.sendAndExpectResponse("""{ "route": "invites/$inviteId/respond", "accepted": true }""")
        }
        clients.forEach { client ->
            val json = client.takeUntilJson {
                it.getText("type") == "InviteView" && it["players"].size() == clients.size
            }
        }

        clients[0].send("""{ "route": "invites/$inviteId/start" }""")

        clients.forEach {client ->
            client.takeUntilJson { it.getText("type") == "GameStarted" }
        }

        clients.forEach {client ->
            client.sendAndExpectResponse("""{ "route": "games/$dslGame/1/view" }""")
            client.takeUntilJson { it.getText("type") == "GameView" }
        }

        // Find game
        val game = server!!.gameSystem.getGameType(dslGame)!!.runningGames["1"]!!
        val gameImpl = game.obj!!.game
        val players = game.players.mapIndexed { index, client ->
            index to clients[playerIds.indexOf(client.playerId!!.toString())]
        }
        var actionCounter = 0

        while (!gameImpl.isGameOver()) {
            if (actionCounter > 10000) {
                throw RuntimeException("Game seems to be stuck. Not finishing after $actionCounter moves. Last view is ${gameImpl.view(0)}")
            }
            actionCounter++
            if (actionCounter % 10 == 0) {
                clients[0].sendAndExpectResponse("""{ "route": "games/$dslGame/1/view" }""")
                clients[0].takeUntilJson { it.getText("type") == "GameView" }
            }
            val actions: List<PlayerGameMoveRequest> = players.mapNotNull {playerClient ->
                val playerIndex = playerClient.first
                val moveHandler = playingMap[gameImpl.model::class]
                if (moveHandler != null) {
                    val controllerContext = GameControllerContext(gameImpl, playerIndex)
                    moveHandler.invoke(controllerContext)?.let {
                        val serialized = gameImpl.actions.type(it.actionType)!!.actionType.serialize(it.parameter)
                        PlayerGameMoveRequest(game, playerIndex, it.actionType, serialized, true)
                    }
                } else {
                    serverAIs.randomAction(game, playerIndex)
                }
            }.map { it.serialize(gameImpl) }
            if (actions.isEmpty()) {
                clients[0].sendAndExpectResponse("""{ "route": "games/$dslGame/1/view" }""")
                val view = clients[0].takeUntilJson { it.getText("type") == "GameView" }
                throw IllegalStateException("Game is not over but no actions available after $actionCounter actions. Is the game a draw? View is $view")
            }
            val request = actions.random() // If multiple players wants to perform an action, just do one of them
            val playerSocket = clients[playerIds.indexOf(game.players[request.player].playerId!!.toString())]
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
