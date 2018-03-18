package net.zomis.games.server2.ws

import klogging.KLoggers
import net.zomis.games.server2.Client
import org.java_websocket.WebSocket

internal class WebClient(val conn: WebSocket): Client() {

    private val logger = KLoggers.logger(this)

    override fun sendData(data: String) {
        if (conn.isOpen) {
            logger.info("Send to $this: $data")
            conn.send(data)
        }
    }

}