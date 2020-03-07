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
import net.zomis.games.server2.clients.FakeClient
import net.zomis.games.server2.invites.clients
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

val nodeFactory = JsonNodeFactory(false)
fun ServerGame.toJson(type: String): ObjectNode {
    return nodeFactory.objectNode()
        .put("type", type)
        .put("gameType", this.gameType.type)
        .put("gameId", this.gameId)
}

data class ServerGameOptions(val database: Boolean)
class ServerGame(val gameType: GameType, val gameId: String, val gameMeta: ServerGameOptions) {
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

    internal val players: MutableList<Client> = mutableListOf()
    var obj: Any? = null

}

data class GameTypeRegisterEvent(val gameType: String)
data class PreMoveEvent(val game: ServerGame, val player: Int, val moveType: String, val move: Any)
data class MoveEvent(val game: ServerGame, val player: Int, val moveType: String, val move: Any)
data class GameStartedEvent(val game: ServerGame)
data class GameEndedEvent(val game: ServerGame)
data class PlayerEliminatedEvent(val game: ServerGame, val player: Int, val winner: WinResult, val position: Int)
data class GameStateEvent(val game: ServerGame, val data: List<Pair<String, Any>>)

data class PlayerGameMoveRequest(val game: ServerGame, val player: Int, val moveType: String, val move: Any) {
    fun illegalMove(reason: String): IllegalMoveEvent {
        return IllegalMoveEvent(game, player, moveType, move, reason)
    }
}

data class IllegalMoveEvent(val game: ServerGame, val player: Int, val moveType: String, val move: Any, val reason: String)

typealias GameIdGenerator = () -> String
class GameType(val type: String, events: EventSystem, private val idGenerator: GameIdGenerator) {

    private val logger = KLoggers.logger(this)
    val runningGames: MutableMap<String, ServerGame> = mutableMapOf()
    val features: Features = Features(events)

    init {
        logger.info("$this has features $features")
    }

    fun createGame(serverGameOptions: ServerGameOptions): ServerGame {
        val game = ServerGame(this, idGenerator(), serverGameOptions)
        runningGames[game.gameId] = game
        logger.info { "Create game with id ${game.gameId} of type $type" }
        return game
    }

    fun resumeGame(gameId: String, game: GameImpl<Any>): ServerGame {
        val serverGame = ServerGame(this, gameId, ServerGameOptions(true))
        serverGame.obj = game
        runningGames[serverGame.gameId] = serverGame
        return serverGame
    }

    fun findOrCreatePlayers(playerIds: List<String>): Collection<Client> {
        return playerIds.map {playerId ->
            this.clients.find { it.playerId.toString() == playerId } ?: FakeClient(UUID.fromString(playerId))
        }
    }

}

class GameSystem {

    private val logger = KLoggers.logger(this)

    data class GameTypes(val gameTypes: MutableMap<String, GameType> = mutableMapOf())
    private val objectMapper = ObjectMapper()
    private lateinit var features: Features

    fun setup(features: Features, events: EventSystem, idGenerator: GameIdGenerator) {
        this.features = features
        val gameTypes = features.addData(GameTypes())
        events.listen("Trigger PlayerGameMoveRequest", ClientJsonMessage::class, {
            it.data.has("gameType") && it.data.getTextOrDefault("type", "") == "move"
        }, {
                val gameType = it.data.get("gameType").asText()
                val moveType = it.data.getTextOrDefault("moveType", "move")
                val move = it.data.get("move")
                val gameId = it.data.get("gameId").asText()

                val game = gameTypes.gameTypes[gameType]?.runningGames?.get(gameId)
                if (game != null) {
                    val playerIndex = game.players.indexOf(it.client)
                    events.execute(PlayerGameMoveRequest(game, playerIndex, moveType, move))
                }
        })

        events.listen("Send GameStarted", ListenerPriority.LATER, GameStartedEvent::class, {true}, {event ->
            sendGameStartedMessages(event.game)
        })
        events.listen("Send GameEnded", GameEndedEvent::class, {true}, {
            it.game.gameOver = true
            it.game.broadcast { _ ->
                it.game.toJson("GameEnded")
            }
        })
        events.listen("Send GameState", GameStateEvent::class, {true}, { event ->
            event.game.broadcast {
                event.stateMessage(it)
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
            gameTypes.gameTypes[it.gameType] = GameType(it.gameType, events, idGenerator)
        })
    }

    fun sendGameStartedMessages(game: ServerGame) {
        val playerNames = game.players
            .asSequence()
            .map { it.name ?: "(unknown)" }
            .fold(objectMapper.createArrayNode()) { arr, name -> arr.add(name) }

        game.players.forEachIndexed { index, client ->
            client.send(game.toJson("GameStarted").put("yourIndex", index).set("players", playerNames))
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

fun GameStateEvent.stateMessage(client: Client?): ObjectNode {
    val node = this.game.toJson("GameState")
    this.data.forEach {
        val value = it.second
        when (value) {
            is Int -> node.put(it.first, value)
            is String -> node.put(it.first, value)
            is Double -> node.put(it.first, value)
            is Boolean -> node.put(it.first, value)
            else -> throw IllegalArgumentException("No support for ${value.javaClass}")
        }
    }
    return node
}
