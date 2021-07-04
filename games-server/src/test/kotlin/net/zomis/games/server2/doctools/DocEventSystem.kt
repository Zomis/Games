package net.zomis.games.server2.doctools

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import net.zomis.core.events.EventSystem
import net.zomis.games.server2.ClientJsonMessage
import net.zomis.games.server2.clients.FakeClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.extension.*
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.util.*
import kotlin.concurrent.getOrSet
import kotlin.reflect.KClass

class DocBlock(val events: EventSystem, val printer: PrintWriter) {

    private val mapper = ObjectMapper()

    fun send(client: FakeClient, documentation: String, message: String) {
        val data = mapper.readTree(message)
        printer.append("$documentation\n\n    $message\n\n")
        events.execute(ClientJsonMessage(client, data))
    }

    fun send(client: FakeClient, message: String) {
        this.send(client, "${client.name} sends:", message)
    }

    fun receive(client: FakeClient, expected: String) {
        assertEquals(expected, client.nextMessage())
        printer.append("${client.name} will receive:\n\n    $expected\n\n")
    }

    fun receive(client: FakeClient, expected: Map<String, Any?>) {
        val actual = client.nextMessage()
        val documentationExcpected = expected.plus("..." to "...")
        val actualMap: Map<String, Any?> = mapper.readValue(actual)
        val match = expected.all { actualMap[it.key] == it.value }

        assertTrue(match) { "Expected\n$documentationExcpected\nbut was\n$actualMap" }
        printer.append("${client.name} will receive:\n\n    ${mapper.writeValueAsString(documentationExcpected)}\n\n")
    }

    fun text(text: String) {
        printer.append("$text\n\n")
    }

}

class DocWriter(val docFile: String = "UNDEFINED") {

    fun document(events: EventSystem, header: String, block: DocBlock.() -> Unit) {
        val printer = PrintWriter(FileWriter(File("documentation/$docFile.md"), true))
        printer.use {
            it.append("### $header\n\n")

            val docBlock = DocBlock(events, printer)
            block.invoke(docBlock)
        }
    }

}
