package net.zomis.games.server2.debug

import com.fasterxml.jackson.databind.ObjectMapper
import klogging.KLogger
import klogging.KLoggers
import net.zomis.core.events.EventSystem
import net.zomis.games.Features
import net.zomis.games.server2.ClientJsonMessage
import net.zomis.games.server2.ConsoleEvent
import net.zomis.games.server2.games.GameSystem
import net.zomis.games.server2.invites.ClientList

class AIGames {

    private val logger = KLoggers.logger(this)

    fun setup(features: Features, events: EventSystem) {
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

}