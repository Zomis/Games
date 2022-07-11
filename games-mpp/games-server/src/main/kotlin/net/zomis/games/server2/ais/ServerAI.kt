package net.zomis.games.server2.ais

import com.fasterxml.jackson.databind.ObjectMapper
import klog.KLoggers
import net.zomis.core.events.EventSystem
import net.zomis.games.dsl.GameListenerFactory
import net.zomis.games.dsl.impl.Game
import net.zomis.games.server2.*
import net.zomis.games.server2.games.*
import java.util.UUID
import java.util.concurrent.Executors

data class AIMoveRequest(val client: Client, val game: ServerGame)
data class DelayedAIMoves(val move: PlayerGameMoveRequest)

val ServerAIProvider = "server-ai"

class ServerAI(
    val gameTypes: List<String>,
    val name: String,
    private val listenerFactory: GameListenerFactory,
    val perform: ServerGameAI,
) {

    private val logger = KLoggers.logger(this)

    private val executor = Executors.newSingleThreadExecutor()
    private val mapper = ObjectMapper()

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
        events.listen("ai move check $name", MoveEvent::class, {
            it.game.players.contains(client)
        }, {
            events.execute(AIMoveRequest(client, it.game))
        })
        events.listen("ai gameStarted check $name", GameStartedEvent::class, {
            it.game.players.contains(client)
        }, {
            events.execute(AIMoveRequest(client, it.game))
        })
        events.listen("ai move $name", AIMoveRequest::class, {it.client == client}, {event ->
            val game = event.game
            val playerIndices = event.game.playerAccess(client).access.filter { it.value >= ClientPlayerAccessType.WRITE }.keys
            if (playerIndices.isEmpty()) {
                return@listen
            }
            executor.submit {
                try {
                    val aiMoves = playerIndices.map {
                        perform.invoke(ServerGameAIContext(game, it, client))
                    }
                    aiMoves.filterNotNull().forEach {singleAIMoves ->
                        events.execute(DelayedAIMoves(singleAIMoves))
                    }
                } catch (e: Exception) {
                    logger.error(e) { "Unable to make move for $this in $game" }
                }
            }
        })

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
