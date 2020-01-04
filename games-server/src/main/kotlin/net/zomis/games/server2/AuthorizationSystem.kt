package net.zomis.games.server2

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.kittinunf.fuel.Fuel
import net.zomis.core.events.EventSystem
import org.slf4j.LoggerFactory
import java.util.UUID

data class ClientLoginEvent(val client: Client, val loginName: String, val provider: String, val token: String)

class AuthorizationSystem {

    private val logger = LoggerFactory.getLogger(AuthorizationSystem::class.java)
    private val mapper = ObjectMapper()

    // Expect: JsonMessage - type="Auth" provider="github" token="..."
    // Provides: ClientLoginEvent

    // Expect: JsonMessage - type="Auth" provider="guest"
    // Provides: ClientLoginEvent

    private val guestRandom = kotlin.random.Random.Default
    private fun fetchGuestUser(events: EventSystem, client: Client) {
        val token: String = guestRandom.nextInt(100000).toString()
        val loginName = "guest-$token"
        logger.info("$client with token (empty) is guest/$loginName")
        client.name = loginName
        client.playerId = UUID.randomUUID()
        events.execute(ClientLoginEvent(client, loginName, "guest", token))
    }

    private fun fetchGithubUser(events: EventSystem, client: Client, token: String) {
        val api =
                Fuel.get("https://api.github.com/user").header(Pair("Authorization", "token $token")).responseString()
        val jsonResult = mapper.readTree(api.third.get())

        val loginName = jsonResult.get("login").asText()
        logger.info("$client with token $token is https://github.com/$loginName")
        client.name = loginName
        client.playerId = UUID.randomUUID()
        events.execute(ClientLoginEvent(client, loginName, "github", token))
    }

    fun register(events: EventSystem) {
        events.listen("Github Authentication", ClientJsonMessage::class, {
            it.data.getTextOrDefault("type", "") == "Auth" &&
                it.data.getTextOrDefault("provider", "") == "github"
        }, {
            fetchGithubUser(events, it.client, it.data.getTextOrDefault("token", ""))
        })
        events.listen("Guest Authentication", ClientJsonMessage::class, {
            it.data.getTextOrDefault("type", "") == "Auth" &&
                it.data.getTextOrDefault("provider", "") == "guest"
        }, {
            fetchGuestUser(events, it.client)
        })

        events.listen("Send Client Login", ClientLoginEvent::class, {true}, {
            it.client.send(mapper.createObjectNode().put("type", "Auth").put("name", it.loginName))
        })
    }

}