package net.zomis.games.server2.games

import net.zomis.core.events.EventSystem
import net.zomis.games.Features
import net.zomis.games.server2.ClientJsonMessage
import net.zomis.games.server2.getTextOrDefault

class GameListSystem {

    fun setup(features: Features, events: EventSystem) {
        val gameTypes = features[GameSystem.GameTypes::class]!!.gameTypes
        // When receiving { type: "GameList" }
        // then respond with { type: "GameList", game, gameId, <some summary info? - send event?> }
        events.listen("process GameList request", ClientJsonMessage::class, {
            it.data.getTextOrDefault("type", "") == "GameList"
        }, {event ->
            val list = gameTypes.asSequence()
                    .flatMap { it.value.runningGames.values.asSequence() }
                    .map {game ->
                        val players = game.playerList()
                        mapOf(
                            "gameType" to game.gameType.type,
                            "gameId" to game.gameId,
                            "players" to players.map { it.name ?: "(unknown)" }
                        )
                    }.toMutableList()
            event.client.send(mapOf("type" to "GameList", "list" to list))
        })
    }

}