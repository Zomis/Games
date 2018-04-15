package net.zomis.games.server2

import net.zomis.games.server2.clients.ur.RandomUrBot
import net.zomis.games.server2.doctools.DocEventSystem

fun main(args: Array<String>) {
    val server2 = Server2(DocEventSystem(testDocWriter()))
    server2.start(testServerConfig())
    println(server2)
    Thread({ RandomUrBot("ws://127.0.0.1:8389").play() }).start()
    Thread({ RandomUrBot("ws://127.0.0.1:8389").play() }).start()

    Thread.sleep(2000) // If this is not here, main will die before new threads start
}
