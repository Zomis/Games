package net.zomis.games.compose.common.network

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import net.zomis.games.compose.common.ClientAuth

abstract class ClientConnection {
    internal val mapper = jacksonObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    var auth: ClientAuth? = null
        private set

    suspend fun send(data: Map<String, Any?>) {
        send(mapper.writeValueAsString(data))
    }

    abstract val events: SharedFlow<JsonNode>
    abstract suspend fun send(data: String)

    suspend fun auth(provider: String, token: String): ClientAuth {
        send("""{ "route": "auth/$provider", "token": "$token" }""")
        val response = events.first {
            it.get("type").asText() == "Auth"
        }
        this.auth = mapper.convertValue<ClientAuth>(response)
        return this.auth!!
    }

    suspend fun joinLobby(keys: Set<String>, maxGames: Int) {
        println("joinLobby $keys")
        send(
            mapOf(
                "route" to "lobby/join",
                "gameTypes" to keys,
                "maxGames" to maxGames
            )
        )
        send("""{ "route": "lobby/list" }""")
        events.collect {
            println(it)
        }
    }

    companion object {

        suspend fun connectWebSocket(httpClient: HttpClient, scope: CoroutineScope, url: String, onConnected: suspend (ClientConnection) -> Unit) {
            httpClient.webSocket(url) {
                val clientConnectionWS = ClientConnectionWS(this, scope)
                scope.launch {
                    onConnected.invoke(clientConnectionWS)
                }
                clientConnectionWS.listen()
            }
        }
    }

}

class ClientConnectionWS(private val connection: DefaultClientWebSocketSession, scope: CoroutineScope) : ClientConnection() {
    override val events: MutableSharedFlow<JsonNode> = MutableSharedFlow()

    override suspend fun send(data: String) {
        println("OUT: $data")
        connection.send(data)
    }

    suspend fun listen() {
        for (frame in connection.incoming) {
            when (frame) {
                is Frame.Text -> {
                    val text = frame.readText()
                    println("IN: $text")
                    events.emit(mapper.readTree(text))
                }
                is Frame.Close -> {
                    println("Close reason: " + frame.readReason())
                }
                else -> {}
            }
        }
        println("No more incoming WebSocket events.")
    }

}