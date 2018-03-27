package net.zomis.games.server2.games

import klogging.KLoggers
import net.zomis.core.events.EventSystem
import net.zomis.games.server2.*
import net.zomis.games.server2.clients.ur.RandomUrBot
import net.zomis.games.server2.games.impl.Connect4

class SimpleMatchMakingSystem(games: GameSystem, events: EventSystem) {

    private val logger = KLoggers.logger(this)
    private val waiting: MutableMap<String, Client> = mutableMapOf()

    init {
        events.addListener(ClientMessage::class, {
            if (it.message != "VUEJS") {
                return@addListener
            }
            val id = games.gameTypes["UR"]?.runningGames?.size ?: 0
            logger.info { "Client connected and no UR-bot waiting, creating id $id" }
            Thread({ RandomUrBot("ws://127.0.0.1:8081").play() }, "ur-bot-$id").start()
        })

        events.addListener(ClientJsonMessage::class, {
            if (it.data.getTextOrDefault("type", "") == "matchMake") {
                val gameType = it.data.get("game").asText()
                if (games.gameTypes[gameType] == null) {
                    logger.warn { "Received unknown gametype: $gameType" }
                    return@addListener
                }

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