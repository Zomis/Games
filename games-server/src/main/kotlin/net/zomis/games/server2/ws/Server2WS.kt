package net.zomis.games.server2.ws

import io.javalin.Javalin
import io.javalin.websocket.*
import klog.KLoggers
import net.zomis.core.events.EventSystem
import net.zomis.games.server2.*

class Server2WS(private val javalin: Javalin, private val events: EventSystem) {

    private val logger = KLoggers.logger(this)
    private val clients = mutableMapOf<WsSession, Client>()

    private fun onOpen(conn: WsSession) {
        val client = WebClient(conn)
        logger.info("Connection opened: " + conn.remoteAddress)
        clients[conn] = client
        client.connected()
        events.execute(ClientConnected(client))
    }

    private fun onClose(conn: WsSession, code: Int, reason: String?) {
        val client = clients.remove(conn)
        logger.info("Connection closed: $conn user $client code $code reason $reason")
        client?.disconnected()
        if (client != null) {
            events.execute(ClientDisconnected(client))
        }
    }

    private fun onMessage(conn: WsSession, message: String?) {
        val client = clients[conn]
        if (client == null) {
            logger.warn { "Message from null--$conn: $message" }
            return
        }
        try {
            logger.info("Message from $client: $message")
            events.execute(ClientMessage(client, message!!))
        } catch (e: Exception) {
            logger.warn(e) { "Error handling $message" }
            events.execute(IllegalClientRequest(client, "Error occurred when processing message"))
        }
    }

    private fun onError(conn: WsSession, ex: Throwable?) {
        val client = clients.remove(conn)
        if (ex == null) {
            logger.warn("Error with unknown message: $client connection $conn")
            return
        }

        val message = ex.toString()
        if (message.contains("Connection timed out") || message.contains("No route to host") || message.contains("Connection reset by peer")) {
            logger.debug("Expected message: $client connection $conn message $message")
            return
        } else {
            logger.warn(ex, "Error with $client connection $conn")
        }

        client?.disconnected()
        if (client != null) {
            events.execute(ClientDisconnected(client))
        }
    }

    fun setup(): Server2WS {
        javalin.ws("websocket") {
            it.onClose(this::onClose)
            it.onMessage(this::onMessage)
            it.onConnect(this::onOpen)
            it.onError(this::onError)
        }
        return this
    }

}
