package net.zomis.games.server2.ws

import io.javalin.websocket.WsSession
import klog.KLoggers
import net.zomis.games.server2.Client

internal class WebClient(val conn: WsSession): Client() {

    private val logger = KLoggers.logger(this)
    private val lock = Any()

    override fun sendData(data: String) {
        synchronized(lock) {
            if (conn.isOpen) {
                logger.info("Send to $this: $data")
                conn.send(data)
            }
        }
    }

}