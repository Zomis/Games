package net.zomis.games.server2

import net.zomis.games.server2.clients.ur.RandomUrBot

fun main(args: Array<String>) {
    val server2 = Server2(8389)
    server2.start(arrayOf())
    println(server2)
    Thread({ RandomUrBot("ws://127.0.0.1:8389").play() }).start()
    Thread({ RandomUrBot("ws://127.0.0.1:8389").play() }).start()

    Thread.sleep(2000)
}
