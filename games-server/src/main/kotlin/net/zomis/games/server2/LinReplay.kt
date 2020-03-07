package net.zomis.games.server2

import io.javalin.Javalin
import klog.KLoggers
import net.zomis.games.server2.db.BadReplayException
import net.zomis.games.server2.db.DBIntegration

class LinReplay(private val dbIntegration: DBIntegration) {

    private val logger = KLoggers.logger(this)
    fun setup(javalin: Javalin) {
        javalin.apply {
            get("/games/:gameid/replay") {ctx ->
                logger.info(ctx.toString())
                val gameId = ctx.pathParam("gameid")
                val dbGame = dbIntegration.loadGame(gameId)
                if (dbGame != null && !dbGame.game.isGameOver()) {
                    throw BadReplayException("Game is not finished after all moves are made. Last view was ${dbGame.views.last()}")
                }
                // For debugging: dbGame.views.map { it["board"] as List<List<Map<String, Any>>> }.map { it.joinToString("\n") { it.joinToString("") { it["owner"].toString()?.takeIf { it != "null" } ?: "_" } } }
                ctx.json(dbGame ?: mapOf("error" to "Game not found: $gameId"))
            }
        }
    }

}
