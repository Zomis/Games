package net.zomis.games.server2.ws

import klogging.KLoggers
import net.zomis.core.events.EventSystem
import net.zomis.games.server2.*
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.lang.Exception
import java.net.InetSocketAddress

class Server2WS(private val events: EventSystem, address: InetSocketAddress) : WebSocketServer(address) {

    private val logger = KLoggers.logger(this)
    private val clients = mutableMapOf<WebSocket, Client>()

    override fun onOpen(conn: WebSocket?, handshake: ClientHandshake?) {
        val client = WebClient(conn!!)
        logger.info("Connection opened: " + conn.remoteSocketAddress)
        clients[conn] = client
        client.connected()
        events.execute(ClientConnected(client))
    }

    override fun onClose(conn: WebSocket?, code: Int, reason: String?, remote: Boolean) {
        val client = clients.remove(conn!!)
        logger.info("Connection closed: $conn user $client code $code reason $reason remote $remote")
        client?.disconnected()
        if (client != null) {
            events.execute(ClientDisconnected(client))
        }
    }

    override fun onMessage(conn: WebSocket?, message: String?) {
        val client = clients[conn!!]
        if (client == null) {
            logger.warn { "Message from null--$conn: $message" }
            return
        }
        try {
            logger.info("Message from $client: $message")
            events.execute(ClientMessage(client, message!!))
        } catch (e: Exception) {
            logger.warn(e, { "Error handling $message" })
            events.execute(IllegalClientRequest(client, "Error occurred when processing message"))
        }
    }

    override fun onError(conn: WebSocket?, ex: Exception?) {
        if (conn == null) {
            logger.error(ex!!, "General error")
            return
        }
        val client = clients.remove(conn)
        val message = ex?.toString() ?: ""
        if (ex == null) {
            logger.warn("Error with unknown message: $client connection $conn")
            return
        }

        if (message.contains("Connection timed out") || message.contains("No route to host") || message.contains("Connection reset by peer")) {
            logger.debug("Expected message: $client connection $conn message $message")
            return
        } else {
            logger.warn(ex, "Error with $client connection $conn")
        }

        client!!.disconnected()
        events.execute(ClientDisconnected(client))
    }

    fun setup(): Server2WS {
        this.start()
        events.listen("Stop WebSocket Server", ShutdownEvent::class, {true}, {e -> this.stop()})
        return this
    }

}
