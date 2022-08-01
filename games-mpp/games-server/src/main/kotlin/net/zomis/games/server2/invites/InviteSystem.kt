package net.zomis.games.server2.invites

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import klog.KLoggers
import net.zomis.games.common.toSingleList
import net.zomis.games.dsl.GameConfigs
import net.zomis.games.dsl.GameSpec
import net.zomis.games.dsl.impl.GameSetupImpl
import net.zomis.games.server2.*
import net.zomis.games.server2.games.*

class InviteTools(
    val removeCallback: (Invite) -> Unit,
    val createGameCallback: (Invite) -> ServerGame,
    val gameClients: GameTypeMap<ClientList>
)
enum class InviteTurnOrder { ORDERED, SHUFFLED }
data class InviteOptions(
    val publicInvite: Boolean,
    val turnOrder: InviteTurnOrder,
    val timeLimit: Int,
    val gameOptions: GameConfigs,
    val database: Boolean
)

data class Invite(
    val playerRange: IntRange,
    val tools: InviteTools,
    val inviteOptions: InviteOptions,
    val gameType: String,
    val id: String,
    val host: Client
) {
    var cancelled: Boolean = false
    private val logger = KLoggers.logger(this)

    private val awaiting: MutableList<Client> = mutableListOf()
    val accepted: MutableList<Client> = mutableListOf()

    val router = MessageRouter(this)
        .handler("respond", this::respond)
        .handler("send", this::sendInvite)
        .handler("start", this::startInvite)
        .handler("cancel", this::cancelInvite)
        .handler("view", this::sendInviteView)

    fun sendInvite(message: ClientJsonMessage) {
        if (cancelled) throw IllegalStateException("Invite is cancelled")
        val inviteTargets = message.data.get("invite")
        val targetClients = inviteTargets.map { it.asText() }.map {playerId ->
            tools.gameClients(gameType)!!.findPlayerId(playerId)
        }.filterIsInstance<Client>().toMutableList()
        this.sendInviteTo(targetClients)
        broadcastInviteView()
    }

    fun sendInviteTo(targetClients: List<Client>) { // It is possible to invite the same AI twice, therefore a list
        if (cancelled) throw IllegalStateException("Invite is cancelled")
        logger.info { "Sending invite $this to $targetClients" }
        this.awaiting.addAll(targetClients)
        targetClients.forEach {
            this.host.send(mapOf("type" to "InviteStatus", "playerId" to it.playerId.toString(), "status" to "pending", "inviteId" to this.id))
            it.send(mapOf("type" to "Invite", "host" to this.host.name, "game" to this.gameType, "inviteId" to this.id))
        }
    }

    fun sendInviteView(message: ClientJsonMessage) = message.client.send(inviteViewMessage())

    fun inviteViewMessage(): Map<String, Any?> = mapOf(
        "type" to "InviteView",
        "inviteId" to this.id,
        "gameType" to this.gameType,
        "cancelled" to this.cancelled,
        "minPlayers" to this.playerRange.first,
        "maxPlayers" to this.playerRange.last,
        "options" to null,
        "gameOptions" to this.inviteOptions.gameOptions.toJSON(),
        "host" to this.host.toMessage(),
        "players" to (listOf(this.host) + this.accepted).map { it.toMessage().plus("playerOptions" to null) },
        "invited" to this.awaiting.map { it.toMessage() }
    )

    fun broadcastInviteView() {
        val clients = listOf(this.host) + this.accepted + this.awaiting
        val message = inviteViewMessage()
        clients.forEach { it.send(message) }
    }

    fun startInvite(message: ClientJsonMessage) {
        if (message.client != this.host) throw IllegalArgumentException("Only invite host can start game")

        this.startCheck()
    }

    fun startCheck(): ServerGame {
        if (cancelled) throw IllegalStateException("Invite is cancelled")
        if (playerCount() in playerRange) {
            return tools.createGameCallback(this)
        } else {
            throw IllegalStateException("Expecting $playerRange players but current is ${playerCount()}")
        }
    }

    fun cancelInvite(message: ClientJsonMessage) {
        if (message.client != this.host) throw IllegalArgumentException("Only invite host can cancel invite")

        logger.info { "Cancelling invite $this" }
        this.cancelled = true
        val inviteCancelledMessage = mapOf("type" to "InviteCancelled", "inviteId" to this.id)
        this.awaiting.forEach {cl ->
            cl.send(inviteCancelledMessage)
        }
        this.accepted.forEach {cl ->
            cl.send(inviteCancelledMessage)
        }
        this.host.send(inviteCancelledMessage)
        this.broadcastInviteView()

        tools.removeCallback(this)
    }

    fun respond(message: ClientJsonMessage) {
        if (cancelled) throw IllegalStateException("Invite is cancelled")
        val response = message.data.get("accepted").asBoolean()
        this.respond(message.client, response)
    }

    fun respond(client: Client, accepted: Boolean) {
        if (cancelled) throw IllegalStateException("Invite is cancelled")
        logger.info { "Client $client responding to invite $this: $accepted" }
        this.host.send(mapOf(
            "type" to "InviteResponse",
            "inviteId" to this.id,
            "playerId" to client.playerId,
            "accepted" to accepted
        ))
        this.awaiting.remove(client)
        if (accepted) {
            this.accepted.add(client)
        }
        broadcastInviteView()
        if (accepted && playerCount() >= playerRange.last) { // Ignore host in this check
            this.startCheck()
        }
    }

    fun playerCount(): Int = 1 + this.accepted.size

}

/**
 * Responsible for inviting specific players
 */
class InviteSystem(
    private val gameClients: GameTypeMap<ClientList>,
    private val createGameCallback: (gameType: String, options: InviteOptions) -> ServerGame,
    private val startGameExecutor: (GameStartedEvent) -> Unit,
    val inviteIdGenerator: () -> String
) {

    private val logger = KLoggers.logger(this)
    private val emptyRouter = MessageRouter<Invite>(null).handler<ClientJsonMessage>("view") {}

    val invites = mutableMapOf<String, Invite>()
    private val dynamicRouter: MessageRouterDynamic<Invite> = { key ->
        this.invites[key]?.router ?: emptyRouter.also { logger.warn { "No such invite: $key" } }
    }
    val router = MessageRouter(this)
        .dynamic(dynamicRouter)
        .handler("start", this::inviteStart)
        .handler("prepare", this::invitePrepare)
        .handler("invite", this::fullInvite)

    private fun removeInvite(invite: Invite) {
        logger.info { "Removing invite ${invite.id}" }
        invites.remove(invite.id)
    }

    fun createInvite(gameType: String, inviteId: String, options: InviteOptions, host: Client, invitees: List<Client>): Invite {
        logger.info { "Creating invite for $gameType id $inviteId host $host invitees $invitees" }
        val playerRange = this.determinePlayerRange(gameType)
        val tools = InviteTools(::removeInvite, ::startInvite, gameClients)
        val invite = Invite(playerRange, tools, options, gameType, inviteId, host)
        invites[inviteId] = invite

        invite.sendInviteTo(invitees)
        invite.broadcastInviteView()
        return invite
    }

    private fun determinePlayerRange(gameType: String): IntRange {
        val gameSpec = ServerGames.games[gameType] ?: return 2..2
        val setup = GameSetupImpl(gameSpec as GameSpec<Any>)
        return setup.playersCount
    }

    private fun invitePrepare(message: ClientJsonMessage) {
        val gameType = message.data.get("gameType")?.asText() ?: throw IllegalArgumentException("Missing field: gameType")
        val setup = ServerGames.setup(gameType)
        val gameOptions = setup?.configs()
        message.client.send(mapOf(
            "type" to "InvitePrepare",
            "gameType" to gameType,
            "playersMin" to setup?.playersCount?.minOrNull(), "playersMax" to setup?.playersCount?.maxOrNull(),
            "config" to gameOptions?.toJSON()
        ))
    }

    private fun inviteStart(message: ClientJsonMessage) {
        val gameType = message.data.get("gameType")?.asText() ?: throw IllegalArgumentException("Missing field: gameType")
        val options = createInviteOptions(gameType, message.data.get("options"), message.data.get("gameOptions"))
        val inviteId = inviteIdGenerator()
        this.createInvite(gameType, inviteId, options, message.client, emptyList())
    }

    private fun createInviteOptions(gameType: String, invite: JsonNode, gameOptionsNode: JsonNode): InviteOptions {
        val setup = ServerGames.setup(gameType)!!
        val gameOptions = JacksonTools.config(setup.configs(), gameOptionsNode)
        return InviteOptions(
            publicInvite = false,//invite["publicInvite"].asBoolean(),
            turnOrder = InviteTurnOrder.ORDERED,// InviteTurnOrder.values().first { it.name == invite["turnOrder"].asText() },
            timeLimit = -1,
            gameOptions = gameOptions,
            database = true//invite["database"].asBoolean()
        )
    }

    private fun fullInvite(message: ClientJsonMessage) {
        val gameType = message.data.get("gameType")?.asText() ?: throw IllegalArgumentException("Missing field: gameType")
        val inviteTargets = message.data.get("invite")
        val inviteId = inviteIdGenerator()
        val targetClients = inviteTargets.map { it.asText() }.map {playerId ->
            gameClients(gameType)!!.findPlayerId(playerId)
        }.filterIsInstance<Client>().toMutableList()

        val defaultConfig = ServerGames.setup(gameType)?.configs() ?: GameConfigs(emptyList())
        val options = InviteOptions(false, InviteTurnOrder.ORDERED, -1, defaultConfig, true)

        this.createInvite(gameType, inviteId, options, message.client, targetClients)
    }

    private fun startInvite(invite: Invite): ServerGame {
        logger.info { "Starting game for invite $invite" }
        val game = createGameCallback(invite.gameType, invite.inviteOptions)
        val clientList = when (invite.inviteOptions.turnOrder) {
            InviteTurnOrder.ORDERED -> invite.host.toSingleList().plus(invite.accepted)
            InviteTurnOrder.SHUFFLED -> invite.accepted.plus(invite.host).shuffled()
        }
        clientList.forEachIndexed { index, client ->
            val access = game.addPlayer(client)
            access.gameAdmin = access.gameAdmin || client == invite.host
            access.addAccess(index, ClientPlayerAccessType.ADMIN)
        }
        removeInvite(invite)
        startGameExecutor(GameStartedEvent(game))
        return game
    }

}