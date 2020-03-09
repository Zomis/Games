package net.zomis.games.server2.invites

import klog.KLoggers
import net.zomis.games.server2.*
import net.zomis.games.server2.games.*

const val autoStartLimit = 2
data class Invite(
    val removeCallback: (Invite) -> Unit,
    val createGameCallback: (Invite) -> Unit,
    val gameType: String,
    val id: String,
    val host: Client
) {
    private val logger = KLoggers.logger(this)

    private val awaiting: MutableList<Client> = mutableListOf()
    val accepted: MutableList<Client> = mutableListOf()

    val router = MessageRouter(this)
        .handler("respond", this::respond)
        .handler("cancel", this::cancelInvite)

    fun sendInviteTo(targetClients: List<Client>) { // It is possible to invite the same AI twice, therefore a list
        logger.info { "Sending invite $this to $targetClients" }
        this.awaiting.addAll(targetClients)
        targetClients.forEach {
            it.send(mapOf("type" to "Invite", "host" to this.host.name, "game" to this.gameType, "inviteId" to this.id))
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

        removeCallback(this)
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
        if (accepted && this.accepted.size >= autoStartLimit - 1) { // Ignore host in this check
            createGameCallback(this)
        }
    }

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
        val invite = Invite(::removeInvite, ::startInvite, gameType, inviteId, host)
        invites[inviteId] = invite

        invite.host.send(mapOf("type" to "InviteWaiting", "inviteId" to invite.id, "waitingFor" to invitees.map { it.name }.toList()))
        invite.sendInviteTo(invitees)
        return invite
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