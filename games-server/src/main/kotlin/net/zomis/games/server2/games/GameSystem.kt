package net.zomis.games.server2.games

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import klogging.KLoggers
import net.zomis.core.events.EventSystem
import net.zomis.games.server2.Client
import net.zomis.games.server2.ClientJsonMessage
import net.zomis.games.server2.getTextOrDefault
import java.util.concurrent.atomic.AtomicInteger

val nodeFactory = JsonNodeFactory(false)
fun Game.toJson(type: String): ObjectNode {
    return nodeFactory.objectNode()
        .put("type", type)
        .put("gameType", this.gameType.type)
        .put("gameId", this.gameId)
}

class Game(val gameType: GameType, val gameId: String) {
    fun broadcast(message: (Client) -> Any) {
        players.forEach { it.send(message.invoke(it)) }
    }

    internal val players: MutableList<Client> = mutableListOf()
    var obj: Any? = null

}

data class GameTypeRegisterEvent(val gameType: String)
data class MoveEvent(val game: Game, val player: Int, val moveType: String, val move: Any)
data class GameStartedEvent(val game: Game)
data class GameEndedEvent(val game: Game)
data class PlayerEliminatedEvent(val game: Game, val player: Int, val winner: Boolean, val position: Int)
data class GameStateEvent(val game: Game, val data: List<Pair<String, Any>>)

data class PlayerGameMoveRequest(val game: Game, val player: Int, val moveType: String, val move: Any) {
    fun illegalMove(reason: String): IllegalMoveEvent {
        return IllegalMoveEvent(game, player, moveType, move, reason)
    }
}

data class IllegalMoveEvent(val game: Game, val player: Int, val moveType: String, val move: Any, val reason: String)

class GameType(val type: String) {

    private val logger = KLoggers.logger(this)
    val runningGames: MutableMap<String, Game> = mutableMapOf()
    private val gameIdCounter = AtomicInteger()

    fun createGame(): Game {
        val gameId = gameIdCounter.incrementAndGet().toString()
        val game = Game(this, gameId)
        runningGames[gameId] = game
        logger.info { "Create game with id $gameId of type $type" }
        return game
    }

}

class GameSystem(events: EventSystem) {

    val gameTypes: MutableMap<String, GameType> = mutableMapOf()

    init {
        val objectMapper = ObjectMapper()
        events.addListener(ClientJsonMessage::class, {
            if (it.data.has("game") && it.data.getTextOrDefault("type", "") == "move") {
                val gameType = it.data.get("game").asText()
                val moveType = it.data.getTextOrDefault("moveType", "move")
                val move = it.data.get("move")
                val gameId = it.data.get("gameId").asText()

                val game= gameTypes[gameType]?.runningGames?.get(gameId)
                if (game != null) {
                    val playerIndex = game.players.indexOf(it.client)
                    events.execute(PlayerGameMoveRequest(game, playerIndex, moveType, move))
                }
            }
        })

        events.addListener(GameStartedEvent::class, {it ->
            val playerNames = it.game.players
                .asSequence()
                .map { it.name ?: "(unknown)" }
                .fold(objectMapper.createArrayNode(), { arr, name -> arr.add(name) })

            it.game.players.forEachIndexed { index, client ->
                client.send(it.game.toJson("GameStarted").put("yourIndex", index).set("players", playerNames))
            }
        })
        events.addListener(GameEndedEvent::class, { it ->
            it.game.broadcast { _ ->
                it.game.toJson("GameEnded")
            }
        })
        events.addListener(GameStateEvent::class, { event ->
            event.game.broadcast {
                event.stateMessage(it)
            }
        })
        events.addListener(MoveEvent::class, {event ->
            event.game.broadcast {
                event.moveMessage()
            }
        })
        events.addListener(PlayerEliminatedEvent::class, {event ->
            event.game.broadcast {
                event.eliminatedMessage()
            }
        })
        events.addListener(IllegalMoveEvent::class, {event ->
            event.game.players[event.player].send(event.game.toJson("IllegalMove")
                .put("player", event.player)
                .put("reason", event.reason)
            )
        })
        events.addListener(GameTypeRegisterEvent::class, {
            gameTypes[it.gameType] = GameType(it.gameType)
        })
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
            .put("winner", this.winner)
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
