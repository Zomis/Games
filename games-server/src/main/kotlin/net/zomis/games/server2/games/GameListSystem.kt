package net.zomis.games.server2.games

import com.fasterxml.jackson.databind.node.JsonNodeFactory
import net.zomis.core.events.EventSystem
import net.zomis.games.Features
import net.zomis.games.server2.ClientJsonMessage
import net.zomis.games.server2.getTextOrDefault

class GameListSystem {

    private val nodeFactory = JsonNodeFactory(false)

    fun setup(features: Features, events: EventSystem) {
        val gameTypes = features[GameSystem.GameTypes::class]!!.gameTypes
        // When receiving { type: "GameList" }
        // then respond with { type: "GameList", game, gameId, <some summary info? - send event?> }
        events.listen("process GameList request", ClientJsonMessage::class, {
            it.data.getTextOrDefault("type", "") == "GameList"
        }, {
            val list = nodeFactory.arrayNode().addAll(
                gameTypes.asSequence()
                    .flatMap { it.value.runningGames.values.asSequence() }
                    .map {
                        val playerNames = it.players
                            .asSequence()
                            .map { it.name ?: "(unknown)" }
                            .fold(nodeFactory.arrayNode(), { arr, name -> arr.add(name) })

                        nodeFactory.objectNode()
                            .put("gameType", it.gameType.type)
                            .put("gameId", it.gameId)
                            .set("players", playerNames)
                    }
                    .toMutableList())
            it.client.send(nodeFactory.objectNode().put("type", "GameList").set("list", list))
        })
    }

}