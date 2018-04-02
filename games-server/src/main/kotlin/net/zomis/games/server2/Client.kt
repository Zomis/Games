package net.zomis.games.server2

import com.fasterxml.jackson.databind.ObjectMapper

open class Client {
    companion object {
        val mapper = ObjectMapper()
    }

    var name: String? = null

    fun connected() {}

    fun disconnected() {}

    fun send(obj: Any) {
        val text = mapper.writeValueAsString(obj)
        sendData(text)
    }

    open fun sendData(data: String) {}
}
