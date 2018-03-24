package net.zomis.games.server2

import net.zomis.core.events.EventSystem
import java.util.*

data class ConsoleEvent(val input: String)

class ServerConsole {

    fun register(events: EventSystem) {
        events.addListener(StartupEvent::class, { e -> start(events) })
    }

    private fun start(events: EventSystem) {
        Thread({ this.run(events) }, "ServerConsole").start()
    }

    private fun run(events: EventSystem) {
        val scanner = Scanner(System.`in`)
        while (true) {
            val input = scanner.nextLine()
            events.execute(ConsoleEvent(input))
            if (input.equals("exit") || input.equals("stop")) {
                events.execute(ShutdownEvent())
                break
            }
        }
    }

}