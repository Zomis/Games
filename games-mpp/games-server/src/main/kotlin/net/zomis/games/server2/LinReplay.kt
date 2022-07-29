package net.zomis.games.server2

import com.github.benmanes.caffeine.cache.Caffeine
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import klog.KLoggers
import kotlinx.coroutines.runBlocking
import net.zomis.games.server2.ais.AIRepository
import net.zomis.games.server2.db.DBGame
import net.zomis.games.server2.db.DBInterface
import java.util.concurrent.TimeUnit

class LinReplay(private val aiRepository: AIRepository, private val dbIntegration: DBInterface) {

    private val caffeine = Caffeine.newBuilder()
        .maximumSize(10_000)
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .refreshAfterWrite(1, TimeUnit.MINUTES)
        .build { key: String -> fetchGame(key) }

    private val logger = KLoggers.logger(this)
    fun setup(routing: Routing) {
        if (true) {
            routing.get("/games/{gameid}/replay") {
                try {
                    val gameId = call.parameters["gameid"]
                    log(this, "fetch replay for $gameId")
                    val dbGame = caffeine.get(gameId!!)!!
                    // For debugging: dbGame.views.map { it["board"] as List<List<Map<String, Any>>> }.map { it.joinToString("\n") { it.joinToString("") { it["owner"].toString()?.takeIf { it != "null" } ?: "_" } } }
                    call.respond(dbGame.toJSON())
                } catch (e: Exception) {
                    logger.error(e) { "Error with replay for game ${call.parameters["gameid"]}" }
                    call.respond(HttpStatusCode.InternalServerError)
                }
            }
            routing.get("/games/{gameid}/analyze/ais") {
                val gameId = call.parameters["gameid"]
                log(this, "get queryable AIs for $gameId")
                val dbGame = caffeine.get(gameId!!)!!
                call.respond(aiRepository.queryableAIs(dbGame.summary.gameType))
                // Get queryable AIs
            }
            routing.get("/games/{gameid}/analyze/{ai}/{position}/{playerindex}") {
                val gameId = call.parameters["gameid"]!!
                val playerIndex = call.parameters["playerindex"]!!.toInt()
                val ai = call.parameters["ai"]!!
                val position = call.parameters["position"]!!.toInt()
                val ignoreCache = call.request.queryParameters["ignoreCache"] == "true"
                log(this, "analyze $gameId $position using $ai")
                val dbGame = if (ignoreCache) fetchGame(gameId) else caffeine.get(gameId)!!
                val game = runBlocking { dbGame.at(this, position) }
                call.respond(aiRepository.analyze(dbGame.summary.gameType, game, ai, playerIndex)!!)
            }
        }
    }

    private fun log(ctx: PipelineContext<Unit, ApplicationCall>, message: String) {
        logger.info("Request to $message from: $ctx")
    }

    private fun fetchGame(gameId: String): DBGame {
        logger.info { "Fetching and caching game $gameId" }
        val game = dbIntegration.loadGameIgnoreErrors(gameId)
        runBlocking {
            if (game != null && !game.game(this).isGameOver()) {
                game.addError("Game is not finished after all ${game.moveHistory.size} moves were made. Last view was ${game.views.last()}")
            }
        }
        return game!!
    }

}
