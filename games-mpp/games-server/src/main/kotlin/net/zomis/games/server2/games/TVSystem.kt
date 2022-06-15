package net.zomis.games.server2.games

import klog.KLoggers
import net.zomis.core.events.EventSystem
import net.zomis.games.Features
import net.zomis.games.server2.Client
import net.zomis.games.server2.ClientJsonMessage
import net.zomis.games.server2.StartupEvent
import net.zomis.games.server2.debug.AIGames
import net.zomis.games.server2.debug.isAI
import net.zomis.games.server2.getTextOrDefault
import net.zomis.games.server2.invites.ClientList
import net.zomis.games.server2.invites.lobbyOptions
import java.util.Random

data class TVData(val viewers: MutableMap<Client, ServerGame>)
// TVData needs to have multiple games depending on viewers' preferences

//data class TVClientData(val games)

val Features.tvData: TVData get() = this[TVData::class]!!

class TVSystem(private val gameClients: GameTypeMap<ClientList>) {

    private val logger = KLoggers.logger(this)
    private val random = Random()

    fun register(features: Features, events: EventSystem) {
        events.listen("start tv", ClientJsonMessage::class, {it.data.getTextOrDefault("type", "") == "tv"}, {
            findOrStartTVGame(events, features.tvData, it.client, features[GameSystem.GameTypes::class]!!.gameTypes)
        })
        events.listen("add tv data", StartupEvent::class, {true}, {
            features.addData(TVData(mutableMapOf()))
        })
        events.listen("switch game for viewers", GameEndedEvent::class, {true}, {event ->
            val playersWatching = features.tvData.viewers.filterValues { it == event.game }.keys
            // Find best gametype that everyone can watch?
            if (playersWatching.isEmpty()) {
                return@listen
            }
            val firstClient = playersWatching.first()
            val nextGame = findOrStartTVGame(events, features.tvData, firstClient,
                    features[GameSystem.GameTypes::class]!!.gameTypes)
            val remainingPlayers = playersWatching.toMutableSet()
            remainingPlayers.remove(firstClient)

            remainingPlayers.forEach {
                makeClientWatch(events, features.tvData, it, nextGame)
            }

            // check viewers
        })
    }

    private fun findOrStartTVGame(events: EventSystem, tvData: TVData, client: Client, gameTypes: MutableMap<String, GameType>): ServerGame {
        val interestingGames = client.lobbyOptions!!.interestingGames
        val possibleTypes = gameTypes.toMutableMap()
        possibleTypes.keys.filter { !interestingGames.contains(it) }.forEach { possibleTypes.remove(it) }
        logger.info { "Finding a game for $client out of $possibleTypes" }

        // check if anyone is already watching a game that we might be interested in
        val anyoneWatching = tvData.viewers
            .filter { it.key != client && !it.value.gameOver && interestingGames.contains(it.value.gameType.type) }
            .map { it.value }
            .firstOrNull()
        logger.info { "Checked what other people is watching, returned $anyoneWatching" }
        if (anyoneWatching != null) {
            makeClientWatch(events, tvData, client, anyoneWatching)
            return anyoneWatching
        }

        // Check for running games
        val runningGame = checkForRunningGames(gameTypes, interestingGames)
        if (runningGame != null) {
            makeClientWatch(events, tvData, client, runningGame)
            return runningGame
        }

        // Start new AI Game
        val choosableTypes = possibleTypes.filter { gameClients(it.key)!!.list().any { it.isAI() } }.keys.toList()
        val gameType = gameTypes[choosableTypes[random.nextInt(choosableTypes.size)]]!!
        logger.info { "Starting a new AI Game of ${gameType.type}" }
        AIGames(gameClients).startNewAIGame(events, gameType.type)
        val nextGame = checkForRunningGames(gameTypes, interestingGames)
        makeClientWatch(events, tvData, client, nextGame!!)
        return nextGame
    }

    private fun checkForRunningGames(gameTypes: MutableMap<String, GameType>, interestingGames: Set<String>): ServerGame? {
        val runningGames = gameTypes.filter { interestingGames.contains(it.key) }.values
                .flatMap { it.runningGames.values }
                .filter { !it.gameOver }
        logger.info { "Running games returns $runningGames" }
        if (!runningGames.isEmpty()) {
            return runningGames[random.nextInt(runningGames.size)]
        }
        return null
    }

    private fun makeClientWatch(events: EventSystem, tvData: TVData, client: Client, nextGame: ServerGame) {
        tvData.viewers[client] = nextGame
        val message = mapOf<String, Any?>(
            "type" to "TVGame",
            "gameType" to nextGame.gameType.type,
            "gameId" to nextGame.gameId,
            "players" to nextGame.playerList(),
            "yourIndex" to -40
        )
        client.send(message)
    }

}