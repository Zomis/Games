package net.zomis.games.server2.doctools

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import klog.KLoggers
import net.zomis.core.events.EventSystem
import net.zomis.games.server2.Client
import net.zomis.games.server2.ClientJsonMessage

class FakeClient : Client() {

    private val logger = KLoggers.logger(this)
    val mapper = ObjectMapper()
    val messages = mutableListOf<String>()

    override fun sendData(data: String) {
        messages.add(data)
        logger.info { "$name received: $data" }
    }

    fun nextMessage(): String {
        return messages.removeAt(0)
    }

    fun clearMessages() {
        return messages.clear()
    }

    fun nextNode(): ObjectNode {
        return mapper.readTree(nextMessage()) as ObjectNode
    }

    fun sendToServer(serverEvents: EventSystem, message: String) {
        val data = mapper.readTree(message)
        serverEvents.execute(ClientJsonMessage(this, data))
    }

}
