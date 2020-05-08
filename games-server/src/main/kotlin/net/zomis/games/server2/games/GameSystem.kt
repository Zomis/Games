package net.zomis.games.server2.games

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
import net.zomis.games.server2.clients.FakeClient
import net.zomis.games.server2.db.DBGame
import net.zomis.games.server2.db.PlayerInGame
import net.zomis.games.server2.invites.ClientList
import net.zomis.games.server2.invites.playerMessage
import java.util.UUID
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
        .handler("join", this::clientJoin)
    var gameOver: Boolean = false
    private val nextMoveIndex = AtomicInteger(0)
    internal val players: MutableList<Client> = mutableListOf()
    internal val observers: MutableSet<Client> = mutableSetOf()
    var obj: Any? = null

    fun broadcast(message: (Client) -> Any) {
        players.forEach { it.send(message.invoke(it)) }
        observers.forEach { it.send(message.invoke(it)) }
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

    private fun clientJoin(message: ClientJsonMessage) {
        // If player should be playing, set as player.
        // Otherwise, set player as observer.

        val playerId = message.client.playerId!!
        val index = players.indexOfFirst { it.playerId == playerId }
        if (index >= 0) {
            players[index] = message.client
        } else {
            observers.add(message.client)
        }
        message.client.send(createGameInfoMessage(message.client))
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

    fun sendGameStartedMessages() {
        val players = this.players.asSequence().map { playerMessage(it) }.toList()
        this.players.forEachIndexed { index, client ->
            client.send(this.map("GameStarted").plus("yourIndex" to index).plus("players" to players))
        }
    }

    private fun createGameInfoMessage(client: Client): Map<String, Any> {
        val players = this.players.asSequence().map { playerMessage(it) }.toList()
        return this.map("GameInfo").plus("yourIndex" to (this.clientPlayerIndex(client) ?: -1)).plus("players" to players)
    }

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
    val gameLoader: (String) -> DBGame?,
    val moveHandler: (PlayerGameMoveRequest) -> Unit
)
class GameType(private val callback: GameCallback, val type: String, private val gameClients: () -> ClientList?,
       events: EventSystem, private val idGenerator: GameIdGenerator) {

    private val logger = KLoggers.logger(this)
    val runningGames: MutableMap<String, ServerGame> = mutableMapOf()
    val features: Features = Features(events)
    private val dynamicRouter: MessageRouterDynamic<ServerGame> = { key ->
        this.runningGames[key]?.router ?: loadGameFromDB(key)?.router ?: throw IllegalArgumentException("Unable to load game $key")
    }

    private fun loadGameFromDB(gameId: String): ServerGame? {
        val dbGame = callback.gameLoader(gameId) ?: return null

        val serverGame = ServerGame(callback, this, gameId, ServerGameOptions(true))
        serverGame.obj = dbGame.game
        runningGames[serverGame.gameId] = serverGame

        fun findOrCreatePlayers(playersInGame: List<PlayerInGame>): Collection<Client> {
            return playersInGame.map {player ->
                val playerId = player.player!!.playerId
                val name = player.player.name
                val uuid = UUID.fromString(playerId)
                gameClients()?.list()?.find { it.playerId.toString() == playerId }
                    ?: FakeClient(uuid).also { it.updateInfo(name, uuid) }
            }
        }
        serverGame.players.addAll(findOrCreatePlayers(dbGame.summary.playersInGame))
        serverGame.sendGameStartedMessages()
        // Do NOT call GameStartedEvent as that will trigger database save

        return serverGame
    }

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

}

class GameSystem(val gameClients: GameTypeMap<ClientList>, private val callback: GameCallback) {

    private val logger = KLoggers.logger(this)

    private val dynamicRouter: MessageRouterDynamic<GameType> = { key -> this.getGameType(key)?.router ?: throw IllegalArgumentException("No such gameType: $key") }
    val router = MessageRouter(this).dynamic(dynamicRouter)

    data class GameTypes(val gameTypes: MutableMap<String, GameType> = mutableMapOf())
    private val objectMapper = ObjectMapper()
    private lateinit var features: Features

    fun setup(features: Features, events: EventSystem, idGenerator: GameIdGenerator) {
        this.features = features
        val gameTypes = features.addData(GameTypes())

        events.listen("Send GameStarted", ListenerPriority.LATER, GameStartedEvent::class, {true}, {event ->
            event.game.sendGameStartedMessages()
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
            gameTypes.gameTypes[it.gameType] = GameType(callback, it.gameType, { gameClients(it.gameType) }, events, idGenerator)
        })
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
