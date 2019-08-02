package net.zomis.games.server2.debug

import com.fasterxml.jackson.databind.ObjectMapper
import klog.KLoggers
import net.zomis.core.events.EventSystem
import net.zomis.games.Features
import net.zomis.games.server2.Client
import net.zomis.games.server2.ClientJsonMessage
import net.zomis.games.server2.ConsoleEvent
import net.zomis.games.server2.games.GameSystem
import net.zomis.games.server2.games.GameType
import net.zomis.games.server2.invites.clients
import java.util.*

fun Client.isAI(): Boolean { return this.name?.startsWith("#AI_") ?: false }

class AIGames {

    private val logger = KLoggers.logger(this)
    private val random = Random()

    fun setup(features: Features, events: EventSystem) {
        // ai UR #AI_Random #AI_Random
        events.listen("AI UR shortcut", ConsoleEvent::class, {it.input == "aiur"}, {
            createURGame(features, events)
        })
        events.listen("AI UR shortcut", ConsoleEvent::class, {it.input == "aiur3"}, {
            createURGame(features, events)
            createURGame(features, events)
            createURGame(features, events)
        })
        events.listen("create AI game", ConsoleEvent::class, {it.input.startsWith("ai ")}, {
            val params = it.input.split(" ")
            val gameTypeName = params[1]
            val player1 = params[2]
            val player2 = params[3]
            val gameType = features[GameSystem.GameTypes::class]!!.gameTypes[gameTypeName]
            if (gameType == null) {
                logger.warn { "No such gameType: $gameType" }
                return@listen
            }
            val clients = gameType.clients
            val client1 = clients.find { it.name == player1 }
            val client2 = clients.find { it.name == player2 }
            if (client1 == null) {
                logger.warn { "Client not found: $client1" }
                return@listen
            }
            if (client2 == null) {
                logger.warn { "Client not found: $client2" }
                return@listen
            }
            startNewGame(events, gameType, client1, client2)
        })
    }

    private fun createURGame(features: Features, events: EventSystem) {
        val ur = features[GameSystem.GameTypes::class]!!.gameTypes["UR"]!!
        startNewAIGame(events, ur)
    }

    fun startNewAIGame(events: EventSystem, gameType: GameType) {
        val ais = gameType.clients.filter { it.isAI() }
        val player1 = ais[random.nextInt(ais.size)]
        val player2 = ais[random.nextInt(ais.size)]
        startNewGame(events, gameType, player1, player2)
    }

    private fun startNewGame(events: EventSystem, gameType: GameType, player1: Client, player2: Client) {
        val gameTypeName = gameType.type
        val data = ObjectMapper().readTree("""{ "type": "Invite", "gameType": "$gameTypeName", "invite": ["${player2.name}"] }""")
        events.execute(ClientJsonMessage(player1, data))
    }

}