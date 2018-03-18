package net.zomis.games.server2

import klogging.KLoggers
import net.zomis.core.events.EventSystem
import net.zomis.games.server2.ws.Server2WS
import java.net.InetSocketAddress

class Server2 {

    private val logger = KLoggers.logger(this)
    private val events = EventSystem()

    fun start(args: Array<String>) {
        events.addListener(StartupEvent::class, {
            val ws = Server2WS(events, InetSocketAddress(8081)).setup()
            logger.info("WebSocket server listening at ${ws.port}")
        })

        events.execute(StartupEvent())
    }

    fun stop() {
        events.execute(ShutdownEvent())
    }

}

fun main(args: Array<String>) {
    Server2().start(args)
}
