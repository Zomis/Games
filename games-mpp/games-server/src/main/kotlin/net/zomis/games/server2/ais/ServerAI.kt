package net.zomis.games.server2.ais

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import klog.KLoggers
import net.zomis.core.events.EventSystem
import net.zomis.games.dsl.GameListenerFactory
import net.zomis.games.dsl.impl.Game
import net.zomis.games.server2.*
import net.zomis.games.server2.games.*
import java.util.UUID

val ServerAIProvider = "server-ai"

class ServerAI(
    val gameTypes: List<String>,
    val name: String,
    private val listenerFactory: GameListenerFactory
) {

    private val logger = KLoggers.logger(this)

    private val mapper = jacksonObjectMapper()

    class AIClient(private val response: (ClientJsonMessage) -> Unit): Client() {
        override fun sendData(data: String) {
            val json = mapper.readTree(data)
            if (json.getTextOrDefault("type", "") != "Invite") {
                return
            }
            //  {"type":"Invite","host":"TestClientA","game":"TestGameType","inviteId":"TestGameType-TestClientA-0"}
            val inviteId = json.get("inviteId").asText()
            val outgoingData = mapper.readTree(mapper.writeValueAsString(
                mapOf("route" to "invites/$inviteId/respond", "accepted" to true)))
            response(ClientJsonMessage(this, outgoingData))
        }
    }

    lateinit var client: AIClient

    fun register(events: EventSystem) {
        this.client = AIClient { events.execute(it) }
        events.execute(ClientConnected(client))
        client.updateInfo(name, UUID.randomUUID())
        events.execute(ClientLoginEvent(client, name, name, ServerAIProvider, name))
        client.listenerFactory = listenerFactory
        val interestingGames = mapper.readTree(mapper.writeValueAsString(mapOf("route" to "lobby/join",
            "gameTypes" to gameTypes, "maxGames" to 100
        )))
        events.execute(ClientJsonMessage(client, interestingGames))
    }

}

fun PlayerGameMoveRequest.serialize(gameImpl: Game<*>): PlayerGameMoveRequest {
    if (this.serialized) return this
    val serializedMove = gameImpl.actions.type(this.moveType)!!
            .actionType.serialize(this.move)
    return PlayerGameMoveRequest(this.client, this.game, this.player, this.moveType, serializedMove, true)
}
