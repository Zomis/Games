package net.zomis.games.ktor.plugins

import io.ktor.client.*
import io.ktor.server.routing.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import net.zomis.games.server2.LinReplay
import net.zomis.games.server2.ServerConfig
import net.zomis.games.server2.ais.AIRepository
import net.zomis.games.server2.db.DBInterface
import net.zomis.games.server2.javalin.auth.LinAuth

fun Application.configureRouting(config: ServerConfig, httpClientFactory: () -> HttpClient, dbInterface: DBInterface?) {

    routing {
        if (config.useOAuth()) {
            LinAuth(config.githubConfig(), config.googleConfig(), httpClientFactory)
                .register(this)
        }
        if (dbInterface != null) {
            LinReplay(AIRepository, dbInterface).setup(this)
        }

        get("/ping") {
            call.respondText("pong")
        }
    }
}
