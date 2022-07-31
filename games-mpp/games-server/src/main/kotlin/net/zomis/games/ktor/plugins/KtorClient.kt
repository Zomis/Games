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
                logger.info("Message from $this connection $wss: $text")
                handler.incomingMessage(this, text)
            }
        } catch (e: Exception) {
//            if (message.contains("Connection timed out") || message.contains("No route to host") || message.contains("Connection reset by peer")) {
//                logger.debug("Expected message: $client connection $conn message $message")
//                return
//            }
            logger.warn(e) { "Connection error in $this" }
        } finally {
            handler.disconnected(this)
        }
    }

    override fun sendData(data: String) {
        runBlocking {
            wss.outgoing.send(Frame.Text(data))
        }
    }
}