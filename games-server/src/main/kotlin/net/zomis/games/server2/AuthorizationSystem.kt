package net.zomis.games.server2

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.kittinunf.fuel.Fuel
import net.zomis.core.events.EventSystem
import org.slf4j.LoggerFactory
import java.util.UUID

data class ClientLoginEvent(val client: Client, val providerId: String, val loginName: String, val provider: String, val token: String)

class AuthorizationSystem(private val events: EventSystem) {
    val router = MessageRouter(this)
        .handler("guest", this::handleGuest)
        .handler("github", this::handleGitHub)
        .handler("google", this::handleGoogle)

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
        client.updateInfo(loginName, UUID.randomUUID())
        events.execute(ClientLoginEvent(client, loginName, loginName, "guest", token))
        sendLoginToClient(client, loginName)
    }

    private fun handleGitHub(message: ClientJsonMessage) {
        val client = message.client
        val token = message.data.getTextOrDefault("token", "")
        val api =
                Fuel.get("https://api.github.com/user").header(Pair("Authorization", "token $token")).responseString()
        val jsonResult = mapper.readTree(api.third.get())

        val loginName = jsonResult.get("login").asText()
        val avatarUrl = jsonResult.getTextOrDefault("avatar_url", "").takeIf { it.isNotEmpty() }
        logger.info("$client with token $token is https://github.com/$loginName")
        client.updateInfo(loginName, UUID.randomUUID(), avatarUrl)
        events.execute(ClientLoginEvent(client, loginName, loginName, "github", token))
        sendLoginToClient(client, loginName)
    }

    private fun handleGoogle(message: ClientJsonMessage) {
        val client = message.client
        val token = message.data.getTextOrDefault("token", "")

        val headers = mapOf("Authorization" to "Bearer $token")
        val result = Fuel.get("https://www.googleapis.com/userinfo/v2/me", emptyList())
            .header(headers)
            .responseString()
        val tree = mapper.readTree(result.third.get())

        val providerName = tree.get("given_name").asText() + " " + tree.get("family_name").asText()
        val avatarUrl = tree.get("picture").asText()

        val id = tree.get("id").asText()

        logger.info("$client with token $token is google/$id/$providerName")
        client.updateInfo(providerName, UUID.randomUUID(), avatarUrl)
        events.execute(ClientLoginEvent(client, id, providerName, "github", token))
        sendLoginToClient(client, providerName)
    }

    private fun sendLoginToClient(client: Client, loginName: String) {
        client.send(mapOf("type" to "Auth", "playerId" to client.playerId, "name" to loginName))
    }

}