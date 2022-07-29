package net.zomis.games.server2

import klog.KLoggers
import net.zomis.core.events.EventSystem
import java.util.*

data class ConsoleEvent(val input: String)

class ServerConsole {

    private val logger = KLoggers.logger(this)

    fun register(events: EventSystem) {
        events.listen("start Server Console", StartupEvent::class, {true}, { start(events) })
        events.listen("print all stack traces", ConsoleEvent::class, {it.input == "threads"}, { printAllThreads() })
        events.listen("stop", ConsoleEvent::class, {it.input == "stop"}, {
            events.execute(ShutdownEvent("stop called"))
        })
    }

    private fun printAllThreads() {
        Thread.getAllStackTraces().forEach {
            val thread = it.key
            val trace = it.value
            logger.info {
                "${thread.name}: Is daemon? ${thread.isDaemon}\n${trace.joinToString("\n")}"
            }
        }
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
                events.execute(ShutdownEvent("console"))
                break
            }
        }
    }

}