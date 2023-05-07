package net.zomis.games.compose.common.network

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

abstract class ClientConnection {
    internal val mapper = jacksonObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    var auth: Message.AuthMessage? = null
        private set

    suspend fun send(data: Map<String, Any?>) {
        send(mapper.writeValueAsString(data))
    }

    protected val _messages = MutableSharedFlow<Message>()
    val messages = _messages.asSharedFlow()
    abstract val events: SharedFlow<JsonNode>
    abstract suspend fun send(data: String)

    suspend fun auth(provider: String, token: String): Message.AuthMessage {
        val result = sendAndAwait(
            mapOf("route" to "auth/$provider", "token" to token),
            await = "Auth", awaitType = Message.AuthMessage::class
        )
        this.auth = result
        return result
    }

    internal suspend fun <T: Message> sendAndAwait(
        data: Map<String, String>,
        await: String,
        awaitType: KClass<T>
    ): T {
        send(data)
        val result = events.first { it.get("type").asText() == await }
        return mapper.convertValue(result, awaitType.java)
    }

    suspend fun joinLobby(keys: Set<String>, maxGames: Int) {
        send(
            mapOf(
                "route" to "lobby/join",
                "gameTypes" to keys,
                "maxGames" to maxGames
            )
        )
    }

    suspend fun updateLobby(): Message.LobbyMessage = sendAndAwait(mapOf("route" to "lobby/list"), await = "Lobby", awaitType = Message.LobbyMessage::class)

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