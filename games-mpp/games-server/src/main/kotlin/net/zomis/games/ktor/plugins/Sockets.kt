package net.zomis.games.ktor.plugins

import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import java.time.Duration
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import net.zomis.games.server2.ws.WebsocketMessageHandler

fun Application.configureSockets(handler: WebsocketMessageHandler) {
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofMinutes(30)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    routing {
        webSocket("/websocket") {
            KtorClient(this).processIncoming(handler)
        }
    }
}
