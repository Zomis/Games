package net.zomis.games.server2.invites

import net.zomis.core.events.EventSystem
import net.zomis.games.Features
import net.zomis.games.server2.*
import net.zomis.games.server2.games.GameStartedEvent
import net.zomis.games.server2.games.GameSystem
import net.zomis.games.server2.games.GameType

data class Invite(val host: Client, val awaiting: MutableList<Client>,
  val accepted: MutableList<Client>, val gameType: GameType, val id: String)
data class InviteEvent(val host: Client, val invite: Invite, val targets: List<Client>)
data class InviteResponseEvent(val source: Client, val invite: Invite, val accepted: Boolean)

/**
 * Responsible for inviting specific players
 */
class InviteSystem {

    val invites = mutableMapOf<String, Invite>()

    fun setup(features: Features, events: EventSystem) {
        val gameTypes = features[GameSystem.GameTypes::class]!!.gameTypes
        events.listen("trigger InviteEvent", ClientJsonMessage::class, {
            it.data.getTextOrDefault("type", "") == "Invite"
        }, {
            val gameType = gameTypes[it.data.getTextOrDefault("gameType", "")]
            if (gameType == null) {
                events.execute(IllegalClientRequest(it.client, "No such gameType"))
                return@listen
            }
            val inviteTargets = it.data.get("invite")
            val inviteId = "${gameType.type}-${it.client.name}-${invites.size}"
            val targetClients = inviteTargets.map { it.asText() }.map {name ->
                gameTypes[gameType.type]!!.clients.filter { it.name == name}.firstOrNull()
            }.filterIsInstance<Client>().toMutableList()
            val invite = Invite(it.client, targetClients, mutableListOf(), gameType, inviteId)
            invites[inviteId] = invite
            val event = InviteEvent(it.client, invite, targetClients.toList())
            events.execute(event)
        })

        events.listen("trigger InviteResponseEvent", ClientJsonMessage::class, {
            it.data.getTextOrDefault("type", "") == "InviteResponse"
        }, {
            val invite = invites[it.data.get("invite").asText()]
            if (invite == null) {
                events.execute(IllegalClientRequest(it.client, "No such invite id"))
                return@listen
            }
            val response = it.data.get("accepted").asBoolean()
            events.execute(InviteResponseEvent(it.client, invite, response))
        })

        events.listen("send invite to target", InviteEvent::class, {true}, { event ->
            event.targets.forEach {
                it.send(mapOf("type" to "Invite", "host" to event.host.name, "game" to event.invite.gameType.type, "inviteId" to event.invite.id))
            }
        })
        events.listen("send invite waiting to host", InviteEvent::class, {true}, {
            it.host.send(mapOf("type" to "InviteWaiting", "inviteId" to it.invite.id, "waitingFor" to it.targets.map { it.name }.toList()))
        })
        events.listen("send invite response", InviteResponseEvent::class, {true}, {
            it.invite.host.send(mapOf("type" to "InviteResponse", "user" to it.source.name, "accepted" to it.accepted, "inviteId" to it.invite.id))
        })
        events.listen("send invite cancelled", InviteResponseEvent::class, {
            it.source == it.invite.host
        }, {
            val message = mapOf("type" to "InviteCancelled", "inviteId" to it.invite.id)
            it.invite.awaiting.forEach {cl ->
                cl.send(message)
            }
            it.invite.accepted.forEach {cl ->
                cl.send(message)
            }
            it.invite.host.send(message)
            invites.remove(it.invite.id)
        })
        events.listen("add user to accepted on invite response", InviteResponseEvent::class, {it.accepted}, {
            it.invite.accepted.add(it.source)
        })
        events.listen("start game on invite response", InviteResponseEvent::class, {it.accepted}, {
            val game = it.invite.gameType.createGame()
            game.players.add(it.invite.host)
            game.players.addAll(it.invite.accepted)
            events.execute(GameStartedEvent(game))
            invites.remove(it.invite.id)
        })
    }

}