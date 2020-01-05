package net.zomis.games.server2.ais

import com.fasterxml.jackson.databind.ObjectMapper
import net.zomis.core.events.EventSystem
import net.zomis.games.server2.Client
import net.zomis.games.server2.ClientConnected
import net.zomis.games.server2.ClientJsonMessage
import net.zomis.games.server2.ClientLoginEvent
import net.zomis.games.server2.games.GameStartedEvent
import net.zomis.games.server2.games.ServerGame
import net.zomis.games.server2.games.MoveEvent
import net.zomis.games.server2.games.PlayerGameMoveRequest
import net.zomis.games.server2.invites.InviteEvent
import net.zomis.games.server2.invites.InviteResponseEvent
import java.util.UUID
import java.util.concurrent.Executors

data class AIMoveRequest(val client: Client, val game: ServerGame)
data class DelayedAIMoves(val moves: List<PlayerGameMoveRequest>)

val ServerAIProvider = "server-ai"

class ServerAI(val gameType: String, val name: String, val perform: (game: ServerGame, playerIndex: Int) -> List<PlayerGameMoveRequest>) {
    private val executor = Executors.newSingleThreadExecutor()

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
        events.listen("ai move $name", AIMoveRequest::class, {it.client == client}, {
            val playerIndex = it.game.players.indexOf(client)
            if (playerIndex < 0) {
                return@listen
            }
            executor.submit {
                val aiMoves = perform.invoke(it.game, playerIndex)
                if (!aiMoves.isEmpty()) {
                    events.execute(DelayedAIMoves(aiMoves))
                }
            }
        })
        events.listen("ServerAI $gameType accept invite $name", InviteEvent::class, {
            it.targets.contains(client)
        }, {
            events.execute(InviteResponseEvent(client, it.invite, true))
        })

        events.execute(ClientConnected(client))
        client.name = name
        client.playerId = UUID.randomUUID()
        events.execute(ClientLoginEvent(client, name, ServerAIProvider, name))
        val mapper = ObjectMapper()
        val interestingGames = mapper.readTree(mapper.writeValueAsString(mapOf("type" to "ClientGames",
            "gameTypes" to listOf(gameType), "maxGames" to 100
        )))
        events.execute(ClientJsonMessage(client, interestingGames))
    }

    val client = Client()

}