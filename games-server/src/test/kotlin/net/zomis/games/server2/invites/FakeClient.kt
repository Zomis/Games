package net.zomis.games.server2.invites

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import net.zomis.games.server2.Client

class FakeClient : Client() {

    val mapper = ObjectMapper()
    val messages = mutableListOf<String>()

    override fun sendData(data: String) {
        messages.add(data)
    }

    fun nextMessage(): String {
        return messages.removeAt(0)
    }

    fun nextNode(): ObjectNode {
        return mapper.readTree(nextMessage()) as ObjectNode
    }

}
