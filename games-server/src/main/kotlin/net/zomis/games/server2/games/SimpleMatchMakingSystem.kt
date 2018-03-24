package net.zomis.games.server2.games

import klogging.KLoggers
import net.zomis.core.events.EventSystem
import net.zomis.games.server2.Client
import net.zomis.games.server2.ClientJsonMessage
import net.zomis.games.server2.games.impl.Connect4

import net.zomis.games.server2.getTextOrDefault

class SimpleMatchMakingSystem(games: GameSystem, events: EventSystem) {

    private val logger = KLoggers.logger(this)
    private val waiting: MutableMap<String, Client> = mutableMapOf()

    init {
        events.addListener(ClientJsonMessage::class, {
            if (it.data.getTextOrDefault("type", "") == "matchMake") {
                val gameType = it.data.get("game").asText()
                synchronized(waiting, {
                if (waiting.containsKey(gameType)) {
                    val opponent = waiting[gameType]!!
                    logger.info { "Pair up $gameType: Waiting $opponent now joining ${it.client}" }

                    val game = games.gameTypes[gameType]!!.createGame()
                    game.players.addAll(listOf(opponent, it.client))
                    events.execute(GameStartedEvent(game))
                    waiting.remove(gameType)
                } else {
                    waiting[gameType] = it.client
                    logger.info { "Now waiting for a match to play $gameType: ${it.client}" }
                }
                })
            }
        })
    }

}