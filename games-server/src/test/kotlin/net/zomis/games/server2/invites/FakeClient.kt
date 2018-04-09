package net.zomis.games.server2.invites

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import klogging.KLoggers
import net.zomis.games.server2.Client

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

    fun nextNode(): ObjectNode {
        return mapper.readTree(nextMessage()) as ObjectNode
    }

}
