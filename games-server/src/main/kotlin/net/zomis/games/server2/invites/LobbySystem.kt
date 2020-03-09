package net.zomis.games.server2.invites

import com.fasterxml.jackson.databind.node.ArrayNode
import klog.KLoggers
import net.zomis.core.events.EventSystem
import net.zomis.games.Features
import net.zomis.games.server2.*
import net.zomis.games.server2.db.UnfinishedGames
import net.zomis.games.server2.games.*

data class ClientInterestingGames(val interestingGames: Set<String>, val maxGames: Int, val currentGames: MutableSet<ServerGame>)
data class ClientList(val clients: MutableSet<Client> = mutableSetOf())

val Client.lobbyOptions: ClientInterestingGames get() = this.interestingGames

class LobbyGameType {
    val clients = ClientList()
}

/**
 * Responsible for informing who is waiting to play which game
 */
class LobbySystem(private val features: Features) {

    private val logger = KLoggers.logger(this)

    val router = MessageRouter(this)
        .handler("join", this::joinLobby)
        .handler("list", this::listLobby)

    private val gameTypes = mutableMapOf<String, LobbyGameType>()

    private fun joinLobby(message: ClientJsonMessage) {
        val interestingGameTypes = (message.data.get("gameTypes") as ArrayNode).map { it.asText() }.toSet()
        val maxGames = message.data.get("maxGames").asInt()
        message.client.interestingGames = ClientInterestingGames(interestingGameTypes, maxGames, mutableSetOf())
        // set interesting games
        // set max number of concurrent games (defaults to 1, -1 = Infinite)
        val newClientMessage = newClientMessage(message.client, interestingGameTypes)
        interestingGameTypes.flatMap { gt ->
            if (gameTypes[gt] == null) {
                logger.warn("Client requested gameType $gt which does not exist.")
                return@flatMap emptyList<Client>()
            }
            gameTypes[gt]!!.clients.clients
        }.toSet().send(newClientMessage)
        interestingGameTypes.forEach { gt ->
            gameTypes[gt]?.clients?.clients?.add(message.client)
        }
        logger.info { "${message.client.name} joined lobby for $interestingGameTypes" }
    }

    private fun listLobby(message: ClientJsonMessage) {
        this.sendAvailableUsers(message.client)
        this.sendUnfinishedGames(message.client)
    }

    fun gameClients(gameType: String): ClientList? {
        return gameTypes[gameType]?.clients
    }

    fun setup(events: EventSystem) {
        events.listen("add ClientList to GameType", GameTypeRegisterEvent::class, { true }, {
            gameTypes[it.gameType] = LobbyGameType()
        })

        events.listen("Lobby mark player as in game", GameStartedEvent::class, { true }, { gameEvent ->
            gameEvent.game.players.map { it.lobbyOptions }.forEach { it.currentGames.add(gameEvent.game) }
        })

        events.listen("Disconnect Client remove ClientInterestingGames", ClientDisconnected::class, { true }, {
            val oldInteresting = it.client.lobbyOptions.interestingGames
            val message = this.disconnectedMessage(it.client)
            oldInteresting.flatMap { gt -> gameTypes[gt]!!.clients.clients }.toSet().send(message)
            oldInteresting.map { gameType -> gameTypes[gameType]!! }.forEach { gameType ->
                gameType.clients.clients.remove(it.client)
            }
        })

        events.listen("Lobby remove player from game", GameEndedEvent::class, { true }, { gameEvent ->
            gameEvent.game.players.map { it.lobbyOptions }.forEach { it.currentGames.remove(gameEvent.game) }
        })
    }
    fun sendAvailableUsers(client: Client) {
        val interestingGames = client.lobbyOptions.interestingGames

        // Return Map<GameType, List<Client name>>
        val resultingMap = mutableMapOf<String, List<String>>()
        gameTypes.entries.forEach {gameType ->
            if (interestingGames.contains(gameType.key)) {
                resultingMap[gameType.key] = gameType.value.clients.clients.filter {
                    val cig = it.interestingGames
                    return@filter cig.maxGames > cig.currentGames.size
                }.filter { it != client }.filter { it.name != null }
                .map { it.name!! }
            }
        }
        client.send(mapOf("type" to "Lobby", "users" to resultingMap))
    }

    private fun sendUnfinishedGames(client: Client) {
        val interestingGames = client.lobbyOptions.interestingGames
        val unfinishedGames = features[UnfinishedGames::class] ?: return

        // Return Map<GameType, List<GameSummary>>
        val games = unfinishedGames.unfinishedGames
            .filter { interestingGames.contains(it.gameType) }
            .filter { it.playersInGame.any { pig ->
                val playerId = pig.player?.playerId ?: ""
                playerId == client.playerId.toString()
            } }
            .groupBy { it.gameType }
            .mapValues { it.value.map { game -> mapOf(
                "GameId" to game.gameId,
                "TimeStarted" to game.timeStarted
            ) } }

        client.send(mapOf("type" to "LobbyUnfinished", "games" to games))
    }

    private fun disconnectedMessage(client: Client): Map<String, Any?> {
        return mapOf(
            "type" to "LobbyChange",
            "client" to client.name,
            "action" to "left"
        )
    }

    private fun newClientMessage(client: Client, interestingGameTypes: Set<String>): Map<String, Any?> {
        return mapOf(
            "type" to "LobbyChange",
            "client" to client.name,
            "action" to "joined",
            "gameTypes" to interestingGameTypes
        )
    }

}