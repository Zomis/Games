package net.zomis.games.server2.invites

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import net.zomis.core.events.EventSystem
import net.zomis.games.Features
import net.zomis.games.server2.*
import net.zomis.games.server2.games.*

data class ClientInterestingGames(val interestingGames: Set<String>, val maxGames: Int, val currentGames: MutableSet<ServerGame>)
data class ListRequest(val client: Client)
data class ClientList(val clients: MutableList<Client> = mutableListOf())

/**
 * Responsible for informing who is waiting to play which game
 */
class LobbySystem {

    fun setup(features: Features, events: EventSystem) {
        val gameTypes = features[GameSystem.GameTypes::class].gameTypes
        val clientData: (Client) -> ClientInterestingGames = {cl -> cl.features[ClientInterestingGames::class]}
//        clientData.register(events)
        events.listen("set client interesting games games", ClientJsonMessage::class, {
            it.data.getTextOrDefault("type", "") == "ClientGames"
        }, {
            val interestingGameTypes = (it.data.get("gameTypes") as ArrayNode).map { it.asText() }.toSet()
            val maxGames = it.data.get("maxGames").asInt()
            it.client.features.addData(ClientInterestingGames(interestingGameTypes, maxGames, mutableSetOf()))
            // set interesting games
            // set max number of concurrent games (defaults to 1, -1 = Infinite)
            interestingGameTypes.forEach { gt -> gameTypes[gt]!!.features[ClientList::class].clients.add(it.client) }
        })

        events.listen("add ClientList to GameType", GameTypeRegisterEvent::class, {true}, {
            gameTypes[it.gameType]!!.features.addData(ClientList())
        })

        events.listen("Lobby mark player as in game", GameStartedEvent::class, {true}, { gameEvent ->
            gameEvent.game.players.map(clientData).forEach { it.currentGames.add(gameEvent.game) }
        })

        events.listen("Disconnect Client remove ClientInterestingGames", ClientDisconnected::class, {true}, {
            val oldInteresting = it.client.features[ClientInterestingGames::class]
            oldInteresting.interestingGames.map { gameType -> gameTypes[gameType]!! }.forEach {gameType ->
                gameType.features[ClientList::class].clients.remove(it.client)
            }
//            it.client.features.remove(ClientInterestingGames::class)
        })

        events.listen("Lobby remove player from game", GameEndedEvent::class, {true}, { gameEvent ->
            gameEvent.game.players.map(clientData).forEach { it.currentGames.remove(gameEvent.game) }
        })

        events.listen("fire ListRequest", ClientJsonMessage::class,
                { it.data.getTextOrDefault("type", "") == "ListRequest" }, {
            events.execute(ListRequest(it.client))
        })

        data class InterestingClient(val client: Client, val gameType: String)
        events.listen("send available users", ListRequest::class, {true}, { event ->
            val interestingGames = clientData(event.client).interestingGames
            val mapper = ObjectMapper()

            // Return Map<GameType, List<Client name>>
            val resultingMap = mutableMapOf<String, List<String>>()
            gameTypes.entries.forEach {gameType ->
                if (interestingGames.contains(gameType.key)) {
                    resultingMap[gameType.key] = gameType.value.features[ClientList::class].clients.filter {
                        val cig = it.features[ClientInterestingGames::class]
                        return@filter cig.maxGames > cig.currentGames.size
                    }.filter { it != event.client }.filter { it.name != null }
                    .map { it.name!! }
                }
            }
            event.client.send(mapOf("type" to "Lobby", "users" to resultingMap))
        })
    }

}