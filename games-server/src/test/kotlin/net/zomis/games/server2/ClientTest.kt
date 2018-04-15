package net.zomis.games.server2

import net.zomis.games.server2.clients.ur.WSClient
import net.zomis.games.server2.doctools.DocEventSystem
import net.zomis.games.server2.doctools.DocWriter
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.net.ServerSocket
import java.net.URI

class ClientTest {
    private val logger = klogging.KLoggers.logger(this)

    var server: Server2? = null
    val config = testServerConfig()

    @RegisterExtension
    @JvmField
    val docWriter: DocWriter = testDocWriter()

    @BeforeEach
    fun startServer() {
        server = Server2(DocEventSystem(docWriter))
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

