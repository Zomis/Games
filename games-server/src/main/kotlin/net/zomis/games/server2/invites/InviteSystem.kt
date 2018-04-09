package net.zomis.games.server2.invites

import net.zomis.core.events.EventSystem
import net.zomis.games.server2.*
import net.zomis.games.server2.games.GameStartedEvent
import net.zomis.games.server2.games.GameSystem
import net.zomis.games.server2.games.GameType

data class Invite(val host: Client, val accepted: MutableList<Client>, val gameType: GameType, val id: String)
//data class InviteCreateEvent(val host: Client, val targets: List<Client>, val gameType: GameType)
data class InviteEvent(val host: Client, val invite: Invite, val targets: List<Client>)
data class InviteResponseEvent(val source: Client, val invite: Invite, val accepted: Boolean)

class InviteSystem(private val games: GameSystem) {

    private val invites = mutableMapOf<String, Invite>()

    fun register(events: EventSystem, clientLookup: ClientsByName) {
        events.listen("trigger InviteEvent", ClientJsonMessage::class, {
            it.data.getTextOrDefault("type", "") == "Invite"
        }, {
            val gameType = games.gameTypes[it.data.getTextOrDefault("gameType", "")]
            if (gameType == null) {
                events.execute(IllegalClientRequest(it.client, "No such gameType"))
                return@listen
            }
            val inviteTargets = it.data.get("invite")
            val inviteId = "${gameType.type}-${it.client.name}-${invites.size}"
            val invite = Invite(it.client, mutableListOf(), gameType, inviteId)
            invites[inviteId] = invite
            val event = InviteEvent(it.client, invite, inviteTargets.map { it.asText() }.map { clientLookup.get(it) }
                .filterIsInstance<Client>())
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
            it.host.send(mapOf("type" to "InviteWaiting", "inviteId" to it.invite.id))
        })
        events.listen("send invite response", InviteResponseEvent::class, {true}, {
            it.invite.host.send(mapOf("type" to "InviteResponse", "user" to it.source.name, "accepted" to it.accepted, "inviteId" to it.invite.id))
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