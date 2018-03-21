package net.zomis.games.server2

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.URI

class ClientTest {
    private val logger = klogging.KLoggers.logger(this)

    var server: Server2? = null

    @BeforeEach
    fun startServer() {
        server = Server2(8378)
        server?.register(ClientMessage::class, { if (it.message == "PING") it.client.sendData("PONG") })
        server!!.start(arrayOf())
    }

    @AfterEach
    fun stopServer() {
        server!!.stop()
    }

    @Test
    fun conn() {
        val client = WSClient(URI("ws://127.0.0.1:8378"))
        client.connectBlocking()
        client.send("PING")
        client.expectExact("PONG")
        Thread.sleep(1000)
        client.close()
    }

}

