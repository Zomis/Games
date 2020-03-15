package net.zomis.games.server2.invites

import klog.KLoggers
import net.zomis.games.dsl.GameSpec
import net.zomis.games.dsl.impl.GameSetupImpl
import net.zomis.games.server2.*
import net.zomis.games.server2.games.*

class InviteTools(
    val removeCallback: (Invite) -> Unit,
    val createGameCallback: (Invite) -> Unit,
    val gameClients: GameTypeMap<ClientList>
)
data class Invite(
    val playerRange: IntRange,
    val tools: InviteTools,
    val gameType: String,
    val id: String,
    val host: Client
) {
    private val logger = KLoggers.logger(this)

    private val awaiting: MutableList<Client> = mutableListOf()
    val accepted: MutableList<Client> = mutableListOf()

    val router = MessageRouter(this)
        .handler("respond", this::respond)
        .handler("send", this::sendInvite)
        .handler("start", this::startInvite)
        .handler("cancel", this::cancelInvite)

    fun sendInvite(message: ClientJsonMessage) {
        val inviteTargets = message.data.get("invite")
        val targetClients = inviteTargets.map { it.asText() }.map {name ->
            tools.gameClients(gameType)!!.clients.firstOrNull { it.name == name }
        }.filterIsInstance<Client>().toMutableList()
        this.sendInviteTo(targetClients)
    }

    fun sendInviteTo(targetClients: List<Client>) { // It is possible to invite the same AI twice, therefore a list
        logger.info { "Sending invite $this to $targetClients" }
        this.awaiting.addAll(targetClients)
        targetClients.forEach {
            it.send(mapOf("type" to "Invite", "host" to this.host.name, "game" to this.gameType, "inviteId" to this.id))
        }
    }

    fun startInvite(message: ClientJsonMessage) {
        if (message.client != this.host) throw IllegalArgumentException("Only invite host can start game")

        this.startCheck()
    }

    fun startCheck() {
        if (playerCount() in playerRange) {
            tools.createGameCallback(this)
        } else {
            throw IllegalStateException("Expecting $playerRange players but current is ${playerCount()}")
        }
    }

    fun cancelInvite(message: ClientJsonMessage) {
        if (message.client != this.host) throw IllegalArgumentException("Only invite host can cancel invite")

        logger.info { "Cancelling invite $this" }
        val inviteCancelledMessage = mapOf("type" to "InviteCancelled", "inviteId" to this.id)
        this.awaiting.forEach {cl ->
            cl.send(inviteCancelledMessage)
        }
        this.accepted.forEach {cl ->
            cl.send(inviteCancelledMessage)
        }
        this.host.send(inviteCancelledMessage)

        tools.removeCallback(this)
    }

    fun respond(message: ClientJsonMessage) {
        val response = message.data.get("accepted").asBoolean()
        this.respond(message.client, response)
    }

    fun respond(client: Client, accepted: Boolean) {
        logger.info { "Client $client responding to invite $this: $accepted" }
        this.host.send(mapOf("type" to "InviteResponse", "user" to client.name, "accepted" to accepted, "inviteId" to this.id))
        this.awaiting.remove(client)
        if (accepted) {
            this.accepted.add(client)
        }
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
    private val createGameCallback: (gameType: String, options: ServerGameOptions) -> ServerGame,
    private val startGameExecutor: (GameStartedEvent) -> Unit
) {

    private val logger = KLoggers.logger(this)

    val invites = mutableMapOf<String, Invite>()
    private val dynamicRouter: MessageRouterDynamic<Invite> = { key -> this.invites[key]?.router ?: throw IllegalArgumentException("No such invite: $key") }
    val router = MessageRouter(this)
        .dynamic(dynamicRouter)
        .handler("start", this::inviteStart)
        .handler("invite", this::fullInvite)

    private fun removeInvite(invite: Invite) {
        logger.info { "Removing invite ${invite.id}" }
        invites.remove(invite.id)
    }

    fun createInvite(gameType: String, inviteId: String, host: Client, invitees: List<Client>): Invite {
        logger.info { "Creating invite for $gameType id $inviteId host $host invitees $invitees" }
        val playerRange = this.determinePlayerRange(gameType)
        val tools = InviteTools(::removeInvite, ::startInvite, gameClients)
        val invite = Invite(playerRange, tools, gameType, inviteId, host)
        invites[inviteId] = invite

        invite.host.send(mapOf("type" to "InviteWaiting", "inviteId" to invite.id, "waitingFor" to invitees.map { it.name }.toList()))
        invite.sendInviteTo(invitees)
        return invite
    }

    private fun determinePlayerRange(gameType: String): IntRange {
        val gameSpec = ServerGames.games[gameType] ?: return 2..2
        val setup = GameSetupImpl(gameSpec as GameSpec<Any>)
        return setup.playersCount
    }

    private fun inviteStart(message: ClientJsonMessage) {
        val gameType = message.data.get("gameType")?.asText() ?: throw IllegalArgumentException("Missing field: gameType")
        val inviteId = "${gameType}-${message.client.name}-${invites.size}"
        this.createInvite(gameType, inviteId, message.client, emptyList())
    }

    private fun fullInvite(message: ClientJsonMessage) {
        val gameType = message.data.get("gameType")?.asText() ?: throw IllegalArgumentException("Missing field: gameType")
        val inviteTargets = message.data.get("invite")
        val inviteId = "${gameType}-${message.client.name}-${invites.size}"
        val targetClients = inviteTargets.map { it.asText() }.map {name ->
            gameClients(gameType)!!.clients.firstOrNull { it.name == name }
        }.filterIsInstance<Client>().toMutableList()

        this.createInvite(gameType, inviteId, message.client, targetClients)
    }

    private fun startInvite(invite: Invite) {
        logger.info { "Starting game for invite $invite" }
        val game = createGameCallback(invite.gameType, ServerGameOptions(true))
        game.players.add(invite.host)
        game.players.addAll(invite.accepted)
        removeInvite(invite)
        startGameExecutor(GameStartedEvent(game))
    }

}