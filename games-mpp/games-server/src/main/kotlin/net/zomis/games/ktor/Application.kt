package net.zomis.games.ktor

import com.fasterxml.jackson.databind.SerializationFeature
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import net.zomis.games.ktor.plugins.configureHTTP
import net.zomis.games.ktor.plugins.configureRouting
import net.zomis.games.ktor.plugins.configureSockets
import net.zomis.games.server2.ServerConfig
import net.zomis.games.server2.db.DBInterface
import net.zomis.games.server2.ws.WebsocketMessageHandler

class KtorApplication(private val handler: WebsocketMessageHandler) {
    fun main(config: ServerConfig, dbInterface: DBInterface?): NettyApplicationEngine {
        return embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
            install(ContentNegotiation) {
                jackson {
                    enable(SerializationFeature.INDENT_OUTPUT)
                }
            }

            configureSockets(handler)
            configureHTTP(config)
            configureRouting(config, dbInterface)
        }.start(wait = true)
    }
}
