package net.zomis.games.compose.common

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.websocket.*

class AppModel {

    val ktorClient = HttpClient(CIO)

    suspend fun ws() {
        ktorClient.webSocket(method = HttpMethod.Get, host = "127.0.0.1", port = 8080, path = "/chat") {
            while(true) {
                val othersMessage = incoming.receive() as? Frame.Text ?: continue
                println(othersMessage.readText())
                val myMessage = readlnOrNull()
                if (myMessage != null) {
                    send(myMessage)
                }
            }
        }
    }


}