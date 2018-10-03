package net.zomis.games.server2

import com.fasterxml.jackson.databind.ObjectMapper
import klogging.KLoggers
import net.zomis.games.Features

open class Client {

    val features = Features(null)

    companion object {
        val mapper = ObjectMapper()
    }

    @Deprecated("Change the client name to a feature")
    var name: String? = null

    fun connected() {}

    fun disconnected() {}

    fun send(obj: Any) {
        val text = mapper.writeValueAsString(obj)
        sendData(text)
    }

    open fun sendData(data: String) {}

    init {
        KLoggers.logger(this).info("$this has features $features")
    }
}
