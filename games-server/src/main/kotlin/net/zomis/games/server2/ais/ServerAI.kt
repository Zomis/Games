package net.zomis.games.server2.ais

import com.fasterxml.jackson.databind.ObjectMapper
import net.zomis.core.events.EventSystem
import net.zomis.games.server2.*
import net.zomis.games.server2.games.GameStartedEvent
import net.zomis.games.server2.games.ServerGame
import net.zomis.games.server2.games.MoveEvent
import net.zomis.games.server2.games.PlayerGameMoveRequest
import java.util.UUID
import java.util.concurrent.Executors

data class AIMoveRequest(val client: Client, val game: ServerGame)
data class DelayedAIMoves(val moves: List<PlayerGameMoveRequest>)

val ServerAIProvider = "server-ai"

class ServerAI(val gameType: String, val name: String, val perform: (game: ServerGame, playerIndex: Int) -> List<PlayerGameMoveRequest>) {
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
            val playerIndex = event.game.players.indices.filter { game.verifyPlayerIndex(client, it) }
            if (playerIndex.isEmpty()) {
                return@listen
            }
            executor.submit {
                val aiMoves = playerIndex.map {
                    perform.invoke(game, it)
                }
                aiMoves.filter { it.isNotEmpty() }.forEach {singleAIMoves ->
                    events.execute(DelayedAIMoves(singleAIMoves))
                }
            }
        })

        this.client = AIClient { events.execute(it) }
        events.execute(ClientConnected(client))
        client.name = name
        client.playerId = UUID.randomUUID()
        events.execute(ClientLoginEvent(client, name, ServerAIProvider, name))
        val interestingGames = mapper.readTree(mapper.writeValueAsString(mapOf("route" to "lobby/join",
            "gameTypes" to listOf(gameType), "maxGames" to 100
        )))
        events.execute(ClientJsonMessage(client, interestingGames))
    }

}