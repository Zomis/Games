package net.zomis.games.server2.debug

import com.fasterxml.jackson.databind.ObjectMapper
import klogging.KLoggers
import net.zomis.core.events.EventSystem
import net.zomis.games.Features
import net.zomis.games.server2.ClientJsonMessage
import net.zomis.games.server2.ConsoleEvent
import net.zomis.games.server2.games.GameSystem
import net.zomis.games.server2.invites.ClientList
import java.util.*

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
            val gameType = features[GameSystem.GameTypes::class].gameTypes[gameTypeName]
            if (gameType == null) {
                logger.warn { "No such gameType: $gameType" }
                return@listen
            }
            val clients = gameType.features[ClientList::class].clients
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
            val data = ObjectMapper().readTree("""{ "type": "Invite", "gameType": "$gameTypeName", "invite": ["$player2"] }""")
            events.execute(ClientJsonMessage(client1, data))
        })
    }

    private fun createURGame(features: Features, events: EventSystem) {
        val ur = features[GameSystem.GameTypes::class].gameTypes["UR"]!!
        val ais = ur.features[ClientList::class].clients.filter { it.name?.startsWith("#AI_") ?: false }.map { it.name!! }
        val player1 = ais[random.nextInt(ais.size)]
        val player2 = ais[random.nextInt(ais.size)]
        events.execute(ConsoleEvent("ai ${ur.type} $player1 $player2"))
    }

}