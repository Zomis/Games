package net.zomis.games.server2.games

import com.fasterxml.jackson.databind.node.ObjectNode
import klogging.KLoggers
import net.zomis.core.events.EventSystem
import net.zomis.games.server2.Client
import net.zomis.games.server2.ClientJsonMessage
import net.zomis.games.server2.getTextOrDefault

data class ObserverStart(val client: Client, val game: Game)
data class ObserverStop(val client: Client, val game: Game)

class ObserverSystem(private val events: EventSystem, private val gameSystem: GameSystem) {

    private val logger = KLoggers.logger(this)

    private val store: MutableMap<Game, MutableList<Any>> = mutableMapOf()
    private val observers: MutableMap<Game, MutableSet<Client>> = mutableMapOf()

    fun register(events: EventSystem) {
        events.addListener(ClientJsonMessage::class, this::observerRequest)

        events.addListener(ObserverStart::class, {
            observers.putIfAbsent(it.game, mutableSetOf())
            observers[it.game]!!.add(it.client)
        })
        events.addListener(ObserverStart::class, {
            store[it.game]?.forEach { mess -> it.client.send(mess) }
        })
        events.addListener(ObserverStop::class, {
            observers[it.game]?.remove(it.client)
            if (observers[it.game]?.isEmpty() == true) {
                observers.remove(it.game)
            }
        })
        events.addListener(GameStartedEvent::class, {
            store[it.game] = mutableListOf()
        })

        events.addListener(MoveEvent::class, {
            addAndSend(it.game, it.moveMessage())
        })
        events.addListener(GameStateEvent::class, {
            addAndSend(it.game, it.stateMessage(null))
        })
        events.addListener(PlayerEliminatedEvent::class, {
            addAndSend(it.game, it.eliminatedMessage())
        })
    }

    private fun addAndSend(game: Game, message: ObjectNode) {
        store[game]?.add(message)
        observers[game]?.forEach { it.send(message) }
    }

    private fun observerRequest(message: ClientJsonMessage) {
        if (message.data.getTextOrDefault("type", "") != "observer") {
            return
        }
        val gameType = gameSystem.gameTypes[message.data.get("game").asText()]
        val gameId = message.data.get("gameId").asText()
        val game = gameType?.runningGames?.get(gameId)
        val observerState = message.data.get("observer").asText()
        if (game == null) {
            logger.warn { "Invalid observer attempt: $message" }
            return
        }

        when (observerState) {
            "start" -> {
                events.execute(ObserverStart(message.client, game))
            }
            "stop" -> {
                events.execute(ObserverStop(message.client, game))
            }
        }

    }

}