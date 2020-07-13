package net.zomis.games.server2

import com.github.benmanes.caffeine.cache.Caffeine
import io.javalin.Context
import io.javalin.Javalin
import klog.KLoggers
import net.zomis.games.server2.ais.AIRepository
import net.zomis.games.server2.db.BadReplayException
import net.zomis.games.server2.db.DBGame
import net.zomis.games.server2.db.DBIntegration
import java.util.concurrent.TimeUnit

class LinReplay(private val aiRepository: AIRepository, private val dbIntegration: DBIntegration) {

    private val caffeine = Caffeine.newBuilder()
        .maximumSize(10_000)
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .refreshAfterWrite(1, TimeUnit.MINUTES)
        .build { key: String -> fetchGame(key) }

    private val logger = KLoggers.logger(this)
    fun setup(javalin: Javalin) {
        javalin.apply {
            get("/games/:gameid/replay") {ctx ->
                val gameId = ctx.pathParam("gameid")
                log(ctx, "fetch replay for $gameId")
                val dbGame = caffeine.get(gameId)!!
                // For debugging: dbGame.views.map { it["board"] as List<List<Map<String, Any>>> }.map { it.joinToString("\n") { it.joinToString("") { it["owner"].toString()?.takeIf { it != "null" } ?: "_" } } }
                ctx.json(dbGame)
            }
            get("/games/:gameid/analyze/ais") {ctx ->
                val gameId = ctx.pathParam("gameid")
                log(ctx, "get queryable AIs for $gameId")
                val dbGame = caffeine.get(gameId)!!
                ctx.json(aiRepository.queryableAIs(dbGame.summary.gameType)!!)
                // Get queryable AIs
            }
            get("/games/:gameid/analyze/:ai/:position/:playerindex") {ctx ->
                val gameId = ctx.pathParam("gameid")
                val playerIndex = ctx.pathParam("playerindex").toInt()
                val ai = ctx.pathParam("ai")
                val position = ctx.pathParam("position").toInt()
                val ignoreCache = ctx.queryParam("ignoreCache") == "true"
                log(ctx, "analyze $gameId $position using $ai")
                val dbGame = if (ignoreCache) fetchGame(gameId) else caffeine.get(gameId)!!
                val game = dbGame.at(position)
                ctx.json(aiRepository.analyze(dbGame.summary.gameType, game, ai, playerIndex)!!)
            }
        }
    }

    private fun log(ctx: Context, message: String) {
        logger.info("Request to $message from IP: ${ctx.ip()}")
    }

    private fun fetchGame(gameId: String): DBGame {
        logger.info { "Fetching and caching game $gameId" }
        val game = dbIntegration.loadGameIgnoreErrors(gameId)
        if (game != null && !game.game.isGameOver()) {
            game.addError("Game is not finished after all ${game.moveHistory.size} moves were made. Last view was ${game.views.last()}")
        }
        return game!!
    }

}
