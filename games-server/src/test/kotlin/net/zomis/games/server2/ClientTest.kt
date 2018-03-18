package net.zomis.games.server2

import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.lang.Exception
import java.net.URI

class ClientTest {
    private val logger = klogging.KLoggers.logger(this)

    var server: Server2? = null

    @BeforeEach
    fun startServer() {
        server = Server2()
        server!!.start(arrayOf())
    }

    @AfterEach
    fun stopServer() {
        server!!.stop()
    }

    @Test
    fun conn() {
        val client = WSClient(URI("ws://127.0.0.1:8081"))
        client.connectBlocking()
        client.send("PING")
        Thread.sleep(1000)
        client.close()
    }

}

class WSClient(uri: URI): WebSocketClient(uri) {
    private val logger = klogging.KLoggers.logger(this)

    override fun onOpen(handshakedata: ServerHandshake?) {
        logger.info { "Open: $handshakedata" }
    }

    override fun onClose(code: Int, reason: String?, remote: Boolean) {
        logger.info { "Close: code $code reason $reason remote $remote" }
    }

    override fun onMessage(message: String?) {
        logger.info { "Recieved: $message" }
    }

    override fun onError(ex: Exception?) {
        logger.info { "Error: $ex" }
    }

}
