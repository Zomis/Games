package net.zomis.games.server2

import klogging.KLoggers
import net.zomis.core.events.EventSystem
import java.util.*

data class ConsoleEvent(val input: String)

class ServerConsole {

    private val logger = KLoggers.logger(this)

    fun register(events: EventSystem) {
        events.addListener(StartupEvent::class, { start(events) })
        events.addListener(ConsoleEvent::class, { printAllThreads(it) })
    }

    private fun printAllThreads(event: ConsoleEvent) {
        if (event.input != "threads") {
            return
        }
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
                events.execute(ShutdownEvent())
                break
            }
        }
    }

}