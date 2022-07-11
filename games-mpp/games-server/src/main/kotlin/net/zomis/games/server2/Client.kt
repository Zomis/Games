package net.zomis.games.server2

import com.fasterxml.jackson.databind.ObjectMapper
import klog.KLoggers
import net.zomis.games.dsl.GameListenerFactory
import net.zomis.games.server2.invites.ClientInterestingGames
import org.apache.commons.codec.digest.DigestUtils
import java.util.UUID

fun Collection<Client>.send(data: Map<String, Any?>) {
    val text = Client.mapper.writeValueAsString(data)
    this.forEach { cl -> cl.sendData(text) }
}

typealias PlayerId = UUID

open class Client {

    private val logger = KLoggers.logger(this)

    companion object {
        val mapper = ObjectMapper()
    }

    var name: String? = null
    var playerId: PlayerId? = null
    var picture: String? = null
    var interestingGames = ClientInterestingGames(emptySet(), 0, mutableSetOf())
    var listenerFactory = GameListenerFactory { _, _ -> null }

    fun connected() {}

    fun disconnected() {}

    fun send(obj: Any) {
        val text = mapper.writeValueAsString(obj)
        sendData(text)
    }

    open fun sendData(data: String) {}

    override fun toString(): String {
        return "${this.javaClass.name}:($playerId/$name)"
    }

    fun updateInfo(name: String, playerId: PlayerId, picture: String? = null) {
        this.name = name
        this.playerId = playerId
        this.picture = picture ?: this.picture ?: "https://www.gravatar.com/avatar/${DigestUtils.md5Hex(playerId.toString())}?s=128&d=identicon"
        logger.info { "Setting client $this to name:$name playerId:$playerId picture:${this.picture}" }
    }

    fun toMessage(): Map<String, Any> {
        return mapOf("id" to this.playerId!!, "name" to name!!, "picture" to picture!!)
    }

}
