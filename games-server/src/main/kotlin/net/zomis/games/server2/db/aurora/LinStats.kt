package net.zomis.games.server2.db.aurora

import io.javalin.Context
import io.javalin.Javalin
import klog.KLoggers

private fun List<String>.filterNotEmpty() = this.filter { it.isNotEmpty() }
class LinStats(private val statsDB: StatsDB) {

    private val logger = KLoggers.logger(this)
    fun setup(javalin: Javalin) {
        javalin.apply {
            get("/stats/query") {ctx ->
                val players = ctx.queryParam("players")
                    ?.split(",")?.filterNotEmpty() ?: emptyList()
                val tags = ctx.queryParam("tags")
                    ?.split(",")?.filterNotEmpty() ?: emptyList()

                log(ctx, "fetch stats for players $players and tags $tags")
                ctx.json(statsDB.query(players = players, tags = tags))
            }
        }
    }

    private fun log(ctx: Context, message: String) {
        logger.info("Request to $message from IP: ${ctx.ip()}")
    }

}