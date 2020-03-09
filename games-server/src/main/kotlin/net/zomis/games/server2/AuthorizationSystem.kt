package net.zomis.games.server2

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.kittinunf.fuel.Fuel
import net.zomis.core.events.EventSystem
import org.slf4j.LoggerFactory
import java.util.UUID

data class ClientLoginEvent(val client: Client, val loginName: String, val provider: String, val token: String)

class AuthorizationSystem(private val events: EventSystem) {
    val router = MessageRouter(this)
        .handler("guest", this::handleGuest)
        .handler("github", this::handleGitHub)

    private val logger = LoggerFactory.getLogger(AuthorizationSystem::class.java)
    private val mapper = ObjectMapper()

    private val guestRandom = kotlin.random.Random.Default
    private fun handleGuest(message: ClientJsonMessage) {
        val client = message.client
        val token: String = guestRandom.nextInt(100000).toString()
        this.handleGuest(client, token)
    }
    fun handleGuest(client: Client, token: String) {
        val loginName = "guest-$token"
        logger.info("$client with token (empty) is guest/$loginName")
        client.name = loginName
        client.playerId = UUID.randomUUID()
        events.execute(ClientLoginEvent(client, loginName, "guest", token))
        sendLoginToClient(client, loginName)
    }

    private fun handleGitHub(message: ClientJsonMessage) {
        val client = message.client
        val token = message.data.getTextOrDefault("token", "")
        val api =
                Fuel.get("https://api.github.com/user").header(Pair("Authorization", "token $token")).responseString()
        val jsonResult = mapper.readTree(api.third.get())

        val loginName = jsonResult.get("login").asText()
        logger.info("$client with token $token is https://github.com/$loginName")
        client.name = loginName
        client.playerId = UUID.randomUUID()
        events.execute(ClientLoginEvent(client, loginName, "github", token))
        sendLoginToClient(client, loginName)
    }

    private fun sendLoginToClient(client: Client, loginName: String) {
        client.send(mapOf("type" to "Auth", "name" to loginName))
    }

}