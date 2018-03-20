package net.zomis.games.server2

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import klogging.KLoggers
import net.zomis.core.events.EventSystem
import net.zomis.games.server2.ws.Server2WS
import java.net.InetSocketAddress

class Server2(val port: Int) {
    private val logger = KLoggers.logger(this)
    private val events = EventSystem()
    private val mapper = ObjectMapper()

    fun start(args: Array<String>) {
        events.addListener(StartupEvent::class, {
            val ws = Server2WS(events, InetSocketAddress(port)).setup()
            logger.info("WebSocket server listening at ${ws.port}")
        })

        events.addListener(ClientMessage::class, {
            if (it.message.startsWith("v1:")) {
                events.execute(ClientJsonMessage(it.client, mapper.readTree(it.message.substring("v1:".length))))
            }
        })
        events.execute(StartupEvent())
    }

    fun stop() {
        events.execute(ShutdownEvent())
    }

}

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        Server2(8081).start(args)
    }
}
