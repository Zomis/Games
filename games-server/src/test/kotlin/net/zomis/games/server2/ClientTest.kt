package net.zomis.games.server2

import net.zomis.games.server2.clients.ur.WSClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.ServerSocket
import java.net.URI

fun testServerConfig(): ServerConfig {
    val config = ServerConfig()
    config.httpPort = 0
    config.wsport = randomPort()
    return config
}

fun randomPort(): Int {
    val tempServer = ServerSocket(0)
    val port = tempServer.localPort
    tempServer.close()
    return port
}

class ClientTest {
    private val logger = klogging.KLoggers.logger(this)

    var server: Server2? = null
    val config = testServerConfig()

    @BeforeEach
    fun startServer() {
        server = Server2()
        server?.events?.listen("PONG", ClientMessage::class, { it.message == "PING" }, { it.client.sendData("PONG") })
        server!!.start(config)
    }

    @AfterEach
    fun stopServer() {
        server!!.stop()
    }

    @Test
    fun conn() {
        val client = WSClient(URI("ws://127.0.0.1:${config.wsport}"))
        client.connectBlocking()
        client.send("PING")
        client.expectExact("PONG")
        Thread.sleep(1000)
        client.close()
    }

}

