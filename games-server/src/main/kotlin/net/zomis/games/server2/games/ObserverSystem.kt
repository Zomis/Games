package net.zomis.games.server2.games

import com.fasterxml.jackson.databind.node.ObjectNode
import klog.KLoggers
import net.zomis.core.events.EventSystem
import net.zomis.games.Features
import net.zomis.games.server2.Client
import net.zomis.games.server2.ClientJsonMessage
import net.zomis.games.server2.getTextOrDefault

data class ObserverStart(val client: Client, val game: ServerGame)
data class ObserverStop(val client: Client, val game: ServerGame)

class ObserverSystem {

    private val logger = KLoggers.logger(this)

    private val store: MutableMap<ServerGame, MutableList<Any>> = mutableMapOf()
    private val observers: MutableMap<ServerGame, MutableSet<Client>> = mutableMapOf()

    fun setup(features: Features, events: EventSystem) {
        val gameSystem = features[GameSystem.GameTypes::class]!!
        events.listen("Fire Observer Request", ClientJsonMessage::class, {
            it.data.getTextOrDefault("type", "") == "observer"
        }, {observerRequest(events, gameSystem, it)})

        events.listen("Add to observer list", ObserverStart::class, {true}, {
            observers.putIfAbsent(it.game, mutableSetOf())
            observers[it.game]!!.add(it.client)
        })
        events.listen("Send all history messages", ObserverStart::class, {true}, {
            store[it.game]?.forEach { mess -> it.client.send(mess) }
        })
        events.listen("Stop observing", ObserverStop::class, {true}, {
            observers[it.game]?.remove(it.client)
            if (observers[it.game]?.isEmpty() == true) {
                observers.remove(it.game)
            }
        })
        events.listen("Create observer-store for new game", GameStartedEvent::class, {true}, {
            store[it.game] = mutableListOf()
        })

        events.listen("Store and send message to observers", MoveEvent::class, {true}, {
            addAndSend(it.game, it.moveMessage())
        })
        events.listen("Store and send move to observers", GameStateEvent::class, {true}, {
            addAndSend(it.game, it.stateMessage(null))
        })
        events.listen("Store and send eliminated to observers", PlayerEliminatedEvent::class, {true}, {
            addAndSend(it.game, it.eliminatedMessage())
        })
    }

    private fun addAndSend(game: ServerGame, message: ObjectNode) {
        store[game]?.add(message)
        observers[game]?.forEach { it.send(message) }
    }

    private fun observerRequest(events: EventSystem, gameSystem: GameSystem.GameTypes, message: ClientJsonMessage) {
        if (message.data.getTextOrDefault("type", "") != "observer") {
            return
        }
        val gameType = gameSystem.gameTypes[message.data.get("gameType").asText()]
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