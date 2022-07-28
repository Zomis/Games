package net.zomis.games.ktor

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import net.zomis.games.ktor.plugins.configureHTTP
import net.zomis.games.ktor.plugins.configureRouting
import net.zomis.games.ktor.plugins.configureSerialization
import net.zomis.games.ktor.plugins.configureSockets

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        configureSockets()
        configureSerialization()
        configureHTTP()
        configureRouting()
    }.start(wait = true)
}
