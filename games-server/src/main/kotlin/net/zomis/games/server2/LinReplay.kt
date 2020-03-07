package net.zomis.games.server2

import io.javalin.Javalin
import klog.KLoggers
import net.zomis.games.server2.db.BadReplayException
import net.zomis.games.server2.db.DBIntegration

class LinReplay(private val dbIntegration: DBIntegration, private val gameSpecs: Map<String, Any>) {

    private val logger = KLoggers.logger(this)
    fun setup(javalin: Javalin) {
        javalin.apply {
            get("/games/:gameid/replay") {ctx ->
                logger.info(ctx.toString())
                val gameId = ctx.pathParam("gameid")
                val dbGame = dbIntegration.loadGame(gameId, gameSpecs)
                if (dbGame != null && !dbGame.game.isGameOver()) {
                    throw BadReplayException("Game is not finished after all moves are made. Last view was ${dbGame.views.last()}")
                }
                ctx.json(dbGame ?: mapOf("error" to "Game not found: $gameId"))
            }
        }
    }

}
