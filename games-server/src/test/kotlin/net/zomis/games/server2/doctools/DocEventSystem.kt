package net.zomis.games.server2.doctools

import net.zomis.core.events.EventSystem
import org.junit.jupiter.api.extension.*
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.util.*
import kotlin.concurrent.getOrSet
import kotlin.reflect.KClass

class DocWriter(val filename: String) : AfterEachCallback, BeforeEachCallback {
    override fun afterEach(context: ExtensionContext?) {
        println("AfterAll $filename")
        file.close()
    }

    override fun beforeEach(context: ExtensionContext?) {
        println("BeforeEach $filename")
        file = PrintWriter(FileWriter(File(filename), true))
    }

    private lateinit var file : PrintWriter

    fun writeConnection(cause: KClass<*>, effect: KClass<*>) {
        file.append("- ${cause.qualifiedName} --> ${effect.qualifiedName}\n")
    }

}

class DocEventThread(val writer: DocWriter) {

    val events = LinkedList<KClass<*>>()

    fun beginExecuting(clazz: KClass<*>) {
        if (events.isEmpty()) {
            events.addLast(clazz)
            return
        }
        val mostRecent = events.peekLast()
        writer.writeConnection(mostRecent, clazz)
        events.addLast(clazz)
    }

    fun finishExecuting() {
        events.removeLast()
    }

}

class DocEventSystem(val docWriter: DocWriter) : EventSystem() {

    private val eventsOnThreads = ThreadLocal<DocEventThread>()

    override fun <E : Any> execute(event: E) {
        beforeExecute(event)
        super.execute(event)
        afterExecute(event)
    }

    private fun <E> afterExecute(event: E) {
        current().finishExecuting()
    }

    private fun current(): DocEventThread {
        return eventsOnThreads.getOrSet { DocEventThread(docWriter) }
    }

    private fun <E : Any> beforeExecute(event: E) {
        current().beginExecuting(event::class)
    }

    // Use a ThreadLocal
    // Before event: Check number of occurrences of "execute" in stacktrace
    // If there are more than 1, then this event got triggered by the previous event
    // Write "StartupEvent->GameTypeRegisteredEvent" to a file
    // TODO: Also write the description of the listener that caused the event

    // At the end, parse this file and create a graph of how events are connected


    // Before event: Check Client messages?
    // After event: Check Client messages?

}
