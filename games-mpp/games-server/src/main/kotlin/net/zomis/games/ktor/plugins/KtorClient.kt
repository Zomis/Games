package net.zomis.games.ktor.plugins

import io.ktor.server.websocket.*
import io.ktor.websocket.*
import klog.KLoggers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.zomis.games.server2.Client
import net.zomis.games.server2.ws.WebsocketMessageHandler

class KtorClient(private val wss: DefaultWebSocketServerSession): Client() {
    private val logger = KLoggers.logger(this)
    // wss.close(CloseReason(CloseReason.Codes.NORMAL, "Bye bye"))

    suspend fun processIncoming(handler: WebsocketMessageHandler) {
        try {
            logger.info("Connection opened: $this connection $wss")
            handler.connected(this)
            for (frame in wss.incoming) {
                if (frame !is Frame.Text) {
                    logger.warn { "Unknown frame: $frame" }
                    continue
                }
                val text = frame.readText()
                try {
                    logger.info("Message from $this connection $wss: $text")
                    handler.incomingMessage(this, text)
                } catch (e: Exception) {
                    logger.error(e) { "Unable to handle frame text: $text" }
                    sendData("""{ "type": "error", "error": "unable to handle message: ${e::class}" }""")
                }
            }
        } catch (e: Throwable) {
//            if (message.contains("Connection timed out") || message.contains("No route to host") || message.contains("Connection reset by peer")) {
//                logger.debug("Expected message: $client connection $conn message $message")
//                return
//            }
            logger.warn(e) { "Connection error in $this" }
        } finally {
            logger.info { "Disconnected $this $wss" }
            handler.disconnected(this)
        }
    }

    override fun sendData(data: String) {
        runBlocking {
            if (!wss.outgoing.isClosedForSend) {
                wss.outgoing.send(Frame.Text(data))
            } else {
                logger.info { "$this is closed for sending. Ignoring: $data" }
            }
        }
    }
}