package net.zomis.games.server2.games

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import klog.KLoggers
import net.zomis.core.events.EventSystem
import net.zomis.core.events.ListenerPriority
import net.zomis.games.Features
import net.zomis.games.WinResult
import net.zomis.games.dsl.PlayerIndex
import net.zomis.games.dsl.impl.GameImpl
import net.zomis.games.server2.*
import net.zomis.games.server2.invites.ClientList
import net.zomis.games.server2.invites.playerMessage
import java.util.concurrent.atomic.AtomicInteger

val nodeFactory = JsonNodeFactory(false)
fun ServerGame.toJson(type: String): ObjectNode {
    return nodeFactory.objectNode()
        .put("type", type)
        .put("gameType", this.gameType.type)
        .put("gameId", this.gameId)
}
fun ServerGame.map(type: String): Map<String, Any> {
    return mapOf("type" to type, "gameType" to this.gameType.type, "gameId" to this.gameId)
}

data class ServerGameOptions(val database: Boolean)
class ServerGame(private val callback: GameCallback, val gameType: GameType, val gameId: String, val gameMeta: ServerGameOptions) {
    private val logger = KLoggers.logger(this)

    private val actionListHandler = ActionListRequestHandler(this)
    val router = MessageRouter(this)
        .handler("view", this::viewRequest)
        .handler("actionList", actionListHandler::sendActionList)
        .handler("action", this::actionRequest)
        .handler("move", this::moveRequest)
    var gameOver: Boolean = false
    private val nextMoveIndex = AtomicInteger(0)

    fun broadcast(message: (Client) -> Any) {
        players.forEach { it.send(message.invoke(it)) }
    }

    fun clientPlayerIndex(client: Client): PlayerIndex {
        return players.indexOf(client).takeIf { it >= 0 }
    }

    fun verifyPlayerIndex(client: Client, playerIndex: Int): Boolean {
        return players.getOrNull(playerIndex) == client
    }

    fun nextMoveIndex(): Int {
        return nextMoveIndex.getAndIncrement()
    }

    private fun actionRequest(message: ClientJsonMessage) {
        // Should not matter if it's an incomplete action or not
        this.actionListHandler.actionRequest(message, callback)
    }

    private fun moveRequest(message: ClientJsonMessage) {
        val moveType = message.data.get("moveType").asText()
        val move = message.data.get("move")
        val playerIndex = if (message.data.has("playerIndex"))
                message.data.get("playerIndex").asInt()
            else clientPlayerIndex(message.client)!!
        if (!verifyPlayerIndex(message.client, playerIndex)) {
            throw IllegalArgumentException("Client ${message.client.name} does not have playerIndex $playerIndex")
        }
        callback.moveHandler(PlayerGameMoveRequest(this, playerIndex, moveType, move))
    }

    private fun viewRequest(message: ClientJsonMessage) {
        if (this.obj !is GameImpl<*>) {
            logger.error("Game $gameId of type $gameType is not a valid DSL game")
            return
        }

        val obj = this.obj as GameImpl<*>
        val viewer = message.client to this.clientPlayerIndex(message.client)
        logger.info { "Sending view data for $gameId of type ${gameType.type} to $viewer" }
        val view = obj.view(viewer.second)
        message.client.send(mapOf(
            "type" to "GameView",
            "gameType" to gameType.type,
            "gameId" to gameId,
            "viewer" to viewer.second,
            "view" to view
        ))
    }

    internal val players: MutableList<Client> = mutableListOf()
    var obj: Any? = null

}

data class GameTypeRegisterEvent(val gameType: String)
data class PreMoveEvent(val game: ServerGame, val player: Int, val moveType: String, val move: Any)
data class MoveEvent(val game: ServerGame, val player: Int, val moveType: String, val move: Any)
data class GameStartedEvent(val game: ServerGame)
data class GameEndedEvent(val game: ServerGame)
data class PlayerEliminatedEvent(val game: ServerGame, val player: Int, val winner: WinResult, val position: Int)

data class PlayerGameMoveRequest(val game: ServerGame, val player: Int, val moveType: String, val move: Any) {
    fun illegalMove(reason: String): IllegalMoveEvent {
        return IllegalMoveEvent(game, player, moveType, move, reason)
    }
}

data class IllegalMoveEvent(val game: ServerGame, val player: Int, val moveType: String, val move: Any, val reason: String)

typealias GameIdGenerator = () -> String
typealias GameTypeMap<T> = (String) -> T?
class GameCallback(
    val moveHandler: (PlayerGameMoveRequest) -> Unit
)
class GameType(val callback: GameCallback, val type: String, events: EventSystem, private val idGenerator: GameIdGenerator) {

    private val logger = KLoggers.logger(this)
    val runningGames: MutableMap<String, ServerGame> = mutableMapOf()
    val features: Features = Features(events)
    private val dynamicRouter: MessageRouterDynamic<ServerGame> = { key -> this.runningGames[key]?.router
            ?: throw IllegalArgumentException("No such gameType: $key") }
    val router = MessageRouter(this).dynamic(dynamicRouter)

    init {
        logger.info("$this has features $features")
    }

    fun createGame(serverGameOptions: ServerGameOptions): ServerGame {
        val game = ServerGame(callback, this, idGenerator(), serverGameOptions)
        runningGames[game.gameId] = game
        logger.info { "Create game with id ${game.gameId} of type $type" }
        return game
    }

    fun resumeGame(gameId: String, game: GameImpl<Any>): ServerGame {
        val serverGame = ServerGame(callback, this, gameId, ServerGameOptions(true))
        serverGame.obj = game
        runningGames[serverGame.gameId] = serverGame
        return serverGame
    }

}

class GameSystem(val gameClients: GameTypeMap<ClientList>) {

    private val logger = KLoggers.logger(this)

    private val dynamicRouter: MessageRouterDynamic<GameType> = { key -> this.getGameType(key)?.router ?: throw IllegalArgumentException("No such invite: $key") }
    val router = MessageRouter(this).dynamic(dynamicRouter)

    data class GameTypes(val gameTypes: MutableMap<String, GameType> = mutableMapOf())
    private val objectMapper = ObjectMapper()
    private lateinit var features: Features

    fun setup(features: Features, events: EventSystem, idGenerator: GameIdGenerator) {
        val callback = GameCallback(
            moveHandler = { events.execute(it) }
        )
        this.features = features
        val gameTypes = features.addData(GameTypes())

        events.listen("Send GameStarted", ListenerPriority.LATER, GameStartedEvent::class, {true}, {event ->
            sendGameStartedMessages(event.game)
        })
        events.listen("Send GameEnded", GameEndedEvent::class, {true}, {
            it.game.gameOver = true
            it.game.broadcast { _ ->
                it.game.toJson("GameEnded")
            }
        })
        events.listen("Send Move", MoveEvent::class, {true}, {event ->
            event.game.broadcast {
                event.moveMessage()
            }
        })
        events.listen("Send PlayerEliminated", PlayerEliminatedEvent::class, {true}, {event ->
            event.game.broadcast {
                event.eliminatedMessage()
            }
        })
        events.listen("Send IllegalMove", IllegalMoveEvent::class, {true}, {event ->
            event.game.players[event.player].send(event.game.toJson("IllegalMove")
                .put("player", event.player)
                .put("reason", event.reason)
            )
        })
        events.listen("Register GameType", GameTypeRegisterEvent::class, {true}, {
            gameTypes.gameTypes[it.gameType] = GameType(callback, it.gameType, events, idGenerator)
        })
    }

    fun sendGameStartedMessages(game: ServerGame) {
        val players = game.players
            .asSequence()
            .map { playerMessage(it) }.toList()

        game.players.forEachIndexed { index, client ->
            client.send(game.map("GameStarted").plus("yourIndex" to index).plus("players" to players))
        }
    }

    fun createGameStartedMessage(game: ServerGame, client: Client): JsonNode {
        val playerNames = game.players
            .asSequence()
            .map { it.name ?: "(unknown)" }
            .fold(objectMapper.createArrayNode()) { arr, name -> arr.add(name) }

        return game.toJson("GameStarted").put("yourIndex", game.clientPlayerIndex(client)).set("players", playerNames)
    }

    fun getGameType(gameType: String): GameType? {
        val gameTypes = features[GameTypes::class]!!
        return gameTypes.gameTypes[gameType]
    }
}

fun MoveEvent.moveMessage(): ObjectNode {
    return this.game.toJson("GameMove")
            .put("player", this.player)
            .put("moveType", this.moveType)
            .putPOJO("move", this.move)
}

fun PlayerEliminatedEvent.eliminatedMessage(): ObjectNode {
    return this.game.toJson("PlayerEliminated")
            .put("player", this.player)
            .put("winner", this.winner.isWinner())
            .put("winResult", this.winner.name)
            .put("position", this.position)
}
