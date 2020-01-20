package net.zomis.games.server2.handlers.games

import klog.KLoggers
import net.zomis.games.dsl.impl.GameImpl
import net.zomis.games.server2.ClientJsonMessage
import net.zomis.games.server2.IncomingMessageHandler
import net.zomis.games.server2.games.GameSystem
import net.zomis.games.server2.getTextOrDefault

class ViewRequestHandler(private val gameSystem: GameSystem): IncomingMessageHandler {
    private val logger = KLoggers.logger(this)

    override fun invoke(message: ClientJsonMessage) {
        val gameType = message.data.getTextOrDefault("gameType", "")
        val type = gameSystem.getGameType(gameType)
        if (type == null) {
            logger.error("No such gameType: $gameType")
            return
        }
        val gameId = message.data.getTextOrDefault("gameId", "")
        val game = type.runningGames[gameId]
        if (game == null) {
            logger.error("No such game: $gameId of type $gameType")
            return
        }

        if (game.obj !is GameImpl<*>) {
            logger.error("Game $gameId of type $gameType is not a valid DSL game")
            return
        }

        val obj = game.obj as GameImpl<*>
        val viewer = message.client to game.clientPlayerIndex(message.client)
        logger.info { "Sending view data for $gameId of type $gameType to $viewer" }
        val view = obj.view(viewer.second)
        message.client.send(mapOf(
            "type" to "GameView",
            "gameType" to gameType,
            "gameId" to gameId,
            "viewer" to viewer.second,
            "view" to view
        ))
    }

}