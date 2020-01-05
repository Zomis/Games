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
import net.zomis.games.server2.*
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

val nodeFactory = JsonNodeFactory(false)
fun ServerGame.toJson(type: String): ObjectNode {
    return nodeFactory.objectNode()
        .put("type", type)
        .put("gameType", this.gameType.type)
        .put("gameId", this.gameId)
}

typealias GameId = UUID
data class ServerGameOptions(val database: Boolean)
class ServerGame(val gameType: GameType, val gameId: String, val gameMeta: ServerGameOptions) {
    var gameOver: Boolean = false
    val uuid = UUID.randomUUID()

    fun broadcast(message: (Client) -> Any) {
        players.forEach { it.send(message.invoke(it)) }
    }

    fun clientPlayerIndex(client: Client): PlayerIndex {
        return players.indexOf(client).takeIf { it >= 0 }
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

class GameType(val type: String, events: EventSystem) {

    private val logger = KLoggers.logger(this)
    val runningGames: MutableMap<String, ServerGame> = mutableMapOf()
    private val gameIdCounter = AtomicInteger()
    val features: Features = Features(events)

    init {
        logger.info("$this has features $features")
    }

    fun createGame(serverGameOptions: ServerGameOptions): ServerGame {
        val gameId = gameIdCounter.incrementAndGet().toString()
        val game = ServerGame(this, gameId, serverGameOptions)
        runningGames[gameId] = game
        logger.info { "Create game with id $gameId of type $type" }
        return game
    }

}

class GameSystem {

    data class GameTypes(val gameTypes: MutableMap<String, GameType> = mutableMapOf())
    private lateinit var features: Features

    fun setup(features: Features, events: EventSystem) {
        this.features = features
        val gameTypes = features.addData(GameTypes())
        val objectMapper = ObjectMapper()
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
            val playerNames = event.game.players
                .asSequence()
                .map { it.name ?: "(unknown)" }
                .fold(objectMapper.createArrayNode()) { arr, name -> arr.add(name) }

            event.game.players.forEachIndexed { index, client ->
                client.send(event.game.toJson("GameStarted").put("yourIndex", index).set("players", playerNames))
            }
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
            gameTypes.gameTypes[it.gameType] = GameType(it.gameType, events)
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
