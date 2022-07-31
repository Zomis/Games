package net.zomis.games.ktor

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import net.zomis.games.ktor.plugins.configureRouting
import net.zomis.games.server2.testServerConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class KtorTest {

    @Test
    fun testRoot() = testApplication {
        application {
            configureRouting(testServerConfig(), dbInterface = null, httpClientFactory = { HttpClient(CIO) })
        }
        client.get("/ping").apply {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals("pong", bodyAsText())
        }
    }

}