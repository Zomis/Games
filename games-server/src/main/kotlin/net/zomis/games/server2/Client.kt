package net.zomis.games.server2

import com.fasterxml.jackson.databind.ObjectMapper
import klog.KLoggers
import net.zomis.games.Features
import java.util.UUID

fun Collection<Client>.send(data: Map<String, Any?>) {
    val text = Client.mapper.writeValueAsString(data)
    this.forEach { cl -> cl.sendData(text) }
}

typealias PlayerId = UUID

open class Client {

    @Deprecated("Features was a bad idea")
    val features = Features(null)

    companion object {
        val mapper = ObjectMapper()
    }

    var name: String? = null

    var playerId: PlayerId? = null

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
