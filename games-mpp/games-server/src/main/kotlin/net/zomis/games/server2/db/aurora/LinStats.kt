package net.zomis.games.server2.db.aurora

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import klog.KLoggers
import net.zomis.core.events.EventSystem
import net.zomis.core.events.ListenerPriority
import net.zomis.games.server2.games.GameEndedEvent

private fun List<String>.filterNotEmpty() = this.filter { it.isNotEmpty() }
class LinStats(private val statsDB: StatsDB) {

    private val logger = KLoggers.logger(this)
    fun setup(events: EventSystem, routing: Routing) {
        events.listen("Save game in stats", ListenerPriority.LATER, GameEndedEvent::class, {true}) {
            statsDB.saveNewlyFinishedInStats()
        }
        routing.apply {
            get("/stats/query") {
                val players = call.request.queryParameters["players"]
                    ?.split(",")?.filterNotEmpty() ?: emptyList()
                val tags = call.request.queryParameters["tags"]
                    ?.split(",")?.filterNotEmpty() ?: emptyList()

                log(this, "fetch stats for players $players and tags $tags")
                call.respond(statsDB.query(players = players, tags = tags))
            }
        }
    }

    private fun log(ctx: PipelineContext<Unit, ApplicationCall>, message: String) {
        logger.info("Request to $message from: $ctx")
    }

}
