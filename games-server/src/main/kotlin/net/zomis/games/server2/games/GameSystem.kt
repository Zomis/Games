package net.zomis.games.server2.games

import klog.KLoggers
import kotlinx.coroutines.sync.Mutex
import net.zomis.core.events.EventSystem
import net.zomis.core.events.ListenerPriority
import net.zomis.games.Features
import net.zomis.games.WinResult
import net.zomis.games.common.PlayerIndex
import net.zomis.games.dsl.GameReplayableImpl
import net.zomis.games.dsl.GameSpec
import net.zomis.games.dsl.GamesImpl
import net.zomis.games.dsl.impl.GameImpl
import net.zomis.games.dsl.impl.GameSetupImpl
import net.zomis.games.server.GamesServer
import net.zomis.games.server2.*
import net.zomis.games.server2.clients.FakeClient
import net.zomis.games.server2.db.DBGame
import net.zomis.games.server2.db.DBIntegration
import net.zomis.games.server2.db.PlayerInGame
import net.zomis.games.server2.invites.ClientList
import net.zomis.games.server2.invites.InviteOptions
import net.zomis.games.server2.invites.InviteTurnOrder
import net.zomis.games.server2.invites.playerMessage
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

fun ServerGame.toJson(type: String): Map<String, Any?> {
    return mapOf(
        "type" to type,
        "gameType" to this.gameType.gameSpec.name,
        "gameId" to this.gameId
    )
}
fun ServerGame.map(type: String): Map<String, Any> {
    return mapOf("type" to type, "gameType" to this.gameType.gameSpec.name, "gameId" to this.gameId)
}

class ServerGame(private val callback: GameCallback, val gameType: GameType, val gameId: String, val gameMeta: InviteOptions) {
    private val logger = KLoggers.logger(this)

    private val actionListHandler = ActionListRequestHandler(this)
    val router = MessageRouter(this)
        .handler("view", this::view)
        .handler("actionList", actionListHandler::sendActionList)
        .handler("action", this::actionRequest)
        .handler("move", this::moveRequest)
        .handler("join", this::clientJoin)
        .handler("viewRequest", this::viewRequest)
    var gameOver: Boolean = false
    private val nextMoveIndex = AtomicInteger(0)
    val mutex = Mutex()
    internal val players: MutableList<Client> = mutableListOf()
    internal val observers: MutableSet<Client> = mutableSetOf()
    var obj: GameReplayableImpl<Any>? = null
    var lastMove: Long = Instant.now().toEpochMilli()

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

    fun nextMoveIndex(): Int = nextMoveIndex.getAndIncrement()
    fun setMoveIndex(next: Int) = nextMoveIndex.set(next)

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
        // Does not matter if it's an incomplete action or not
        this.actionListHandler.actionRequest(message, callback)
    }

    @Deprecated("Replace with action instead. This approach is only *required* for non-DSL games which no longer exists")
    private fun moveRequest(message: ClientJsonMessage) {
        val moveType = message.data.get("moveType").asText()
        val move = message.data.get("move")
        val playerIndex = if (message.data.has("playerIndex"))
                message.data.get("playerIndex").asInt()
            else clientPlayerIndex(message.client)!!
        if (!verifyPlayerIndex(message.client, playerIndex)) {
            throw IllegalArgumentException("Client ${message.client.name} does not have playerIndex $playerIndex")
        }
        callback.moveHandler(PlayerGameMoveRequest(this, playerIndex, moveType, move, true))
    }

    private fun view(message: ClientJsonMessage) {
        val obj = this.obj!!.game
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

    private fun viewRequest(message: ClientJsonMessage) {
        val viewer = message.client to this.clientPlayerIndex(message.client)

        val viewDetailsResult = this.obj!!.game.viewRequest(viewer.second,
              message.data.getTextOrDefault("viewRequest", ""), emptyMap())

        message.client.send(mapOf(
            "type" to "GameViewDetails",
            "gameType" to gameType.type,
            "gameId" to gameId,
            "viewer" to viewer.second,
            "details" to viewDetailsResult
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

    fun gameSetup(): GameSetupImpl<Any> {
        return ServerGames.setup(this.gameType.type)!!
    }

}

class GameTypeRegisterEvent(spec: GameSpec<*>) {
    val gameSpec = spec as GameSpec<Any>
    val gameType: String = gameSpec.name
}

data class PreMoveEvent(val game: ServerGame, val player: Int, val moveType: String, val move: Any)
data class MoveEvent(val game: ServerGame, val player: Int, val moveType: String, val move: Any)
data class GameStartedEvent(val game: ServerGame)
data class GameEndedEvent(val game: ServerGame)
data class PlayerEliminatedEvent(val game: ServerGame, val player: Int, val winner: WinResult, val position: Int)

data class PlayerGameMoveRequest(val game: ServerGame, val player: Int, val moveType: String, val move: Any, val serialized: Boolean) {
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
class GameType(private val callback: GameCallback, val gameSpec: GameSpec<Any>, private val gameClients: () -> ClientList?,
       events: EventSystem, private val idGenerator: GameIdGenerator, private val dbIntegration: DBIntegration?) {

    val type: String = gameSpec.name
    private val logger = KLoggers.logger(this)
    val runningGames: MutableMap<String, ServerGame> = mutableMapOf()
    val features: Features = Features(events)
    private val dynamicRouter: MessageRouterDynamic<ServerGame> = { key ->
        this.runningGames[key]?.router ?: loadGameFromDB(key)?.router ?: throw IllegalArgumentException("Unable to load game $key")
    }

    private fun loadGameFromDB(gameId: String): ServerGame? {
        val dbGame = callback.gameLoader(gameId) ?: return null
        val gameOptions = dbGame.summary.gameConfig
        val loadGameOptions = InviteOptions(false, InviteTurnOrder.ORDERED, -1, gameOptions, true)
        val serverGame = ServerGame(callback, this, gameId, loadGameOptions)
        serverGame.setMoveIndex(dbGame.moveHistory.size)
        serverGame.obj = GamesImpl.game(gameSpec).replay(dbGame.replayData(), GamesServer.replayStorage.database(dbIntegration!!, gameId)).replayable()
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

    fun createGame(serverGameOptions: InviteOptions): ServerGame {
        val gameId = idGenerator()
        val game = ServerGame(callback, this, gameId, serverGameOptions)
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
    private lateinit var features: Features

    fun setup(features: Features, events: EventSystem, idGenerator: GameIdGenerator, dbIntegration: () -> DBIntegration? = { null }) {
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
            event.game.players[event.player].send(
                event.game.toJson("IllegalMove").plus("player" to event.player).plus("reason" to event.reason)
            )
        })
        events.listen("Register GameType", GameTypeRegisterEvent::class, {true}, {
            gameTypes.gameTypes[it.gameType] = GameType(callback, it.gameSpec, { gameClients(it.gameType) }, events, idGenerator, dbIntegration())
        })
    }

    fun getGameType(gameType: String): GameType? {
        val gameTypes = features[GameTypes::class]!!
        return gameTypes.gameTypes[gameType]
    }
}

fun MoveEvent.moveMessage(): Map<String, Any?> {
    val serverGame = this.game
    val gameImpl = serverGame.obj!!.game
    val actionType = gameImpl.actions.type(this.moveType)!!.actionType
    val moveData = actionType.serialize(this.move)
    return this.game.toJson("GameMove")
        .plus("player" to this.player)
        .plus("moveType" to this.moveType)
        .plus("move" to moveData)
}

fun PlayerEliminatedEvent.eliminatedMessage(): Map<String, Any?> {
    return this.game.toJson("PlayerEliminated")
        .plus("player" to this.player)
        .plus("winner" to this.winner.isWinner())
        .plus("winResult" to this.winner.name)
        .plus("position" to this.position)
}
