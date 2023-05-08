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
import net.zomis.games.server2.ClientMessage
import kotlin.reflect.KClass

abstract class ClientConnection {
    internal val mapper = jacksonObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    var auth: Message.AuthMessage? = null
        private set

    suspend fun send(data: ClientToServerMessage) {
        send(mapper.writeValueAsString(data))
    }

    protected val _messages = MutableSharedFlow<Message>()
    val messages = _messages.asSharedFlow()
    abstract val events: SharedFlow<JsonNode>
    abstract suspend fun send(data: String)

    suspend fun auth(provider: String, token: String): Message.AuthMessage {
        val result: Message.AuthMessage = sendAndAwait(ClientToServerMessage.AuthRequest(provider, token))
        this.auth = result
        return result
    }

    internal suspend inline fun <reified T: Message> sendAndAwait(
        message: ClientToServerMessage
    ): T = sendAndAwait(message, awaitType = T::class)

    internal suspend fun <T: Message> sendAndAwait(
        data: ClientToServerMessage,
        awaitType: KClass<T>
    ): T {
        send(data)
        val result = messages.filter { awaitType.isInstance(it) }.first()
        return mapper.convertValue(result, awaitType.java)
    }

    suspend fun joinLobby(keys: Set<String>, maxGames: Int) {
        send(ClientToServerMessage.JoinLobby(gameTypes = keys, maxGames = maxGames))
    }

    suspend fun updateLobby(): Message.LobbyMessage = sendAndAwait(ClientToServerMessage.ListLobby)

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
                    val frameInput = mapper.readTree(text)
                    events.emit(frameInput)
                    val typeName = frameInput.get("type")?.asText()
                    val type = Message.messageType(typeName ?: "undefined")
                    if (type == null) {
                        println("WARNING: No such type $typeName")
                    } else {
                        val message = mapper.convertValue(frameInput, type.java)
                        _messages.emit(message)
                    }
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