package net.zomis.games.server2.games

import klog.KLoggers
import kotlinx.coroutines.sync.Mutex
import net.zomis.core.events.EventSystem
import net.zomis.core.events.ListenerPriority
import net.zomis.games.Features
import net.zomis.games.WinResult
import net.zomis.games.common.PlayerIndex
import net.zomis.games.common.isObserver
import net.zomis.games.dsl.ActionType
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
import net.zomis.games.server2.invites.*
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
enum class ClientPlayerAccessType { NONE, READ, WRITE, ADMIN }
data class ClientAccess(var gameAdmin: Boolean) {
    val access: MutableMap<Int, ClientPlayerAccessType> = mutableMapOf()
    fun index(playerIndex: PlayerIndex): ClientPlayerAccessType {
        if (playerIndex.isObserver() || playerIndex!! < 0) return ClientPlayerAccessType.READ // Observer
        return access[playerIndex] ?: ClientPlayerAccessType.NONE
    }

    fun addAccess(playerIndex: Int, access: ClientPlayerAccessType): ClientAccess {
        this.access[playerIndex] = access
        return this
    }

    override fun toString(): String = "Access:($gameAdmin, $access)"
}

class ServerGame(private val callback: GameCallback, val gameType: GameType, val gameId: String, val gameMeta: InviteOptions) {
    private val logger = KLoggers.logger(this)

    private val actionListHandler = ActionListRequestHandler(this)
    val router = MessageRouter(this)
        .handler("view", this::view)
        .handler("meta", this::meta)
        .handler("actionList", actionListHandler::sendActionList)
        .handler("action", this::actionRequest)
        .handler("move", this::moveRequest)
        .handler("join", this::clientJoin)
        .handler("viewRequest", this::viewRequest)
    var gameOver: Boolean = false
    private val nextMoveIndex = AtomicInteger(0)
    val mutex = Mutex()
    internal val players: MutableMap<Client, ClientAccess> = mutableMapOf()
    val playerCount: Int get() = players.values.flatMap { it.access.keys }.distinct().count()
    var obj: GameReplayableImpl<Any>? = null
    var lastMove: Long = Instant.now().toEpochMilli()

    fun broadcast(message: (Client) -> Any) {
        players.keys.forEach { it.send(message.invoke(it)) }
    }
    fun playerAccess(client: Client): ClientAccess = players.getOrDefault(client, ClientAccess(gameAdmin = false))
    fun addPlayer(client: Client): ClientAccess {
        return players.getOrPut(client) { ClientAccess(gameAdmin = false) }
    }
    fun requireAccess(client: Client, playerIndex: Int?, requiredAccess: ClientPlayerAccessType) {
        val actual = playerAccess(client).index(playerIndex)
        if (actual < requiredAccess) {
            throw IllegalStateException("$this: Client $client playerIndex $playerIndex has $actual but required $requiredAccess, all accesses are $players")
        }
    }

    fun nextMoveIndex(): Int = nextMoveIndex.getAndIncrement()
    fun setMoveIndex(next: Int) = nextMoveIndex.set(next)

    private fun clientJoin(message: ClientJsonMessage) {
        // If player should be playing, set as player.
        // Otherwise, set player as observer.

        val playerId = message.client.playerId!!
        val access = players.entries.find { it.key.playerId == playerId }?.value ?: ClientAccess(gameAdmin = false)
        players[message.client] = access
        message.client.send(createGameInfoMessage(message.client))
    }

    fun meta(message: ClientJsonMessage) {
        /*
        Make it possible to:
        - Switch which player is playing in game
        - Add a user to a playerIndex, with either read or read-write permissions, or read-write-admin permissions
        - Count observers also as users
        - Read permissions: See available actions, see view
        - Write permissions: Perform actions
        - Admin permissions: Allow giving access to other users
        - Log all meta-actions, show to all players

        - GAME ADMIN: Reset game, set new playerCount
        - GAME ADMIN: Add more players to game, start with: Set, Decrypto, Liar's Dice?, Skull?
        - GAME ADMIN: Remove players from game, Avalon and Hanabi cannot be replaced by AI (well, it *could*...) and needs special handling

        - Requires either admin permission over the user or site-wide admin permission
        */
    }

    private fun actionRequest(message: ClientJsonMessage) {
        if (!this.actionListHandler.actionRequest(message, callback)) {
            // If it's an incomplete action and only a choice step, broadcast view update
            val actionStepMessage = this.toJson("UpdateView")
            this.broadcast { actionStepMessage }
        }
    }

    @Deprecated("Replace with action instead. This approach is only *required* for non-DSL games which no longer exists")
    private fun moveRequest(message: ClientJsonMessage) {
        val moveType = message.data.get("moveType").asText()
        val move = message.data.get("move")
        val playerIndex = message.data.get("playerIndex").asInt()
        requireAccess(message.client, playerIndex, ClientPlayerAccessType.WRITE)
        callback.moveHandler(PlayerGameMoveRequest(message.client, this, playerIndex, moveType, move, true))
    }

    private fun view(message: ClientJsonMessage) {
        val obj = this.obj!!.game
        val viewer = message.data.get("playerIndex").asInt().takeIf { it >= 0 }

        val chosenActionType = message.data.get("actionType")?.asText()
        val chosen = JsonChoices.deserialize(obj, message.data.get("chosen"), viewer, chosenActionType)
        if (viewer != null) {
            obj.actions.choices.setChosen(viewer, chosenActionType, chosen)
        }

        requireAccess(message.client, viewer, ClientPlayerAccessType.READ)
        logger.info { "Sending view data for $viewer in $gameId of type ${gameType.type} to ${message.client}" }
        val view = obj.view(viewer)
        message.client.send(mapOf(
            "type" to "GameView",
            "gameType" to gameType.type,
            "gameId" to gameId,
            "viewer" to viewer,
            "view" to view
        ))
    }

    private fun viewRequest(message: ClientJsonMessage) {
        val viewer = message.data.get("playerIndex").asInt().takeIf { it >= 0 }
        requireAccess(message.client, viewer, ClientPlayerAccessType.READ)
        val viewDetailsResult = this.obj!!.game.viewRequest(viewer,
              message.data.getTextOrDefault("viewRequest", ""), emptyMap())

        message.client.send(mapOf(
            "type" to "GameViewDetails",
            "gameType" to gameType.type,
            "gameId" to gameId,
            "viewer" to viewer,
            "details" to viewDetailsResult
        ))
    }

    fun sendGameStartedMessages() {
        this.players.keys.forEach {
            sendGameStartedMessage(it)
        }
    }

    fun sendGameStartedMessage(client: Client) {
        client.send(
            this.toJson("GameStarted")
                .plus("access" to playerAccess(client).access)
                .plus("players" to playerList().map { it.toMap() })
        )
    }

    fun playerList(): List<PlayerInfo> {
        val playerIndices = this.players.flatMap { it.value.access.keys }.distinct().sorted()
        return playerIndices.map { highestAccessTo(it)!! }.map { playerMessage(it) }.toList()
    }

    fun highestAccessTo(playerIndex: Int): Client? = this.players.maxBy { it.value.index(playerIndex) }?.key

    private fun createGameInfoMessage(client: Client): Map<String, Any?> {
        return this.toJson("GameInfo")
            .plus("access" to playerAccess(client).access).plus("players" to playerList())
    }

    fun gameSetup(): GameSetupImpl<Any> = ServerGames.setup(this.gameType.type)!!

}

class GameTypeRegisterEvent(spec: GameSpec<*>) {
    val gameSpec = spec as GameSpec<Any>
    val gameType: String = gameSpec.name
}

data class PreMoveEvent(val game: ServerGame, val player: Int, val moveType: String, val move: Any)
data class MoveEvent<T: Any, A: Any>(val game: ServerGame, val player: Int, val actionType: ActionType<T, A>, val parameter: A)
data class GameStartedEvent(val game: ServerGame)
data class GameEndedEvent(val game: ServerGame)
data class PlayerEliminatedEvent(val game: ServerGame, val player: Int, val winner: WinResult, val position: Int)

data class PlayerGameMoveRequest(val client: Client, val game: ServerGame, val player: Int, val moveType: String, val move: Any, val serialized: Boolean) {
    fun illegalMove(reason: String): IllegalMoveEvent {
        return IllegalMoveEvent(client, game, player, moveType, move, reason)
    }
}

data class IllegalMoveEvent(val client: Client, val game: ServerGame, val player: Int, val moveType: String, val move: Any, val reason: String)

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

        fun findOrCreatePlayers(playersInGame: List<PlayerInGame>): Map<Client, ClientAccess> {
            return playersInGame.associate {player ->
                val playerId = player.player!!.playerId
                val name = player.player.name
                val uuid = UUID.fromString(playerId)
                val client = gameClients()?.list()?.find { it.playerId.toString() == playerId }
                    ?: FakeClient(uuid).also { it.updateInfo(name, uuid) }
                client to ClientAccess(gameAdmin = false).addAccess(player.playerIndex, ClientPlayerAccessType.ADMIN)
            }
        }
        serverGame.players.putAll(findOrCreatePlayers(dbGame.summary.playersInGame))
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
            event.client.send(
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

fun MoveEvent<*, *>.moveMessage(): Map<String, Any?> {
    return this.game.toJson("GameMove")
        .plus("player" to this.player)
        .plus("moveType" to this.actionType.name)
}

fun PlayerEliminatedEvent.eliminatedMessage(): Map<String, Any?> {
    return this.game.toJson("PlayerEliminated")
        .plus("player" to this.player)
        .plus("winner" to this.winner.isWinner())
        .plus("winResult" to this.winner.name)
        .plus("position" to this.position)
}
