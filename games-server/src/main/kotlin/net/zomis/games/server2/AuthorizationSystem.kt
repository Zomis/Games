package net.zomis.games.server2

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.kittinunf.fuel.Fuel
import net.zomis.core.events.EventSystem
import org.slf4j.LoggerFactory
import java.util.*

data class ClientLoginEvent(val client: Client, val loginName: String, val provider: String)

class AuthorizationSystem {

    private val logger = LoggerFactory.getLogger(AuthorizationSystem::class.java)
    private val mapper = ObjectMapper()

    // Expect: JsonMessage - type="Auth" provider="github" token="..."
    // Provides: ClientLoginEvent

    // Expect: JsonMessage - type="Auth" provider="guest"
    // Provides: ClientLoginEvent

    private val guestRandom = Random()
    fun fetchGuestUser(events: EventSystem, client: Client) {
        val loginName = "guest-" + guestRandom.nextInt(100000)
        logger.info("$client with token (empty) is guest/$loginName")
        client.name = loginName
        events.execute(ClientLoginEvent(client, loginName, "guest"))
    }

    fun fetchGithubUser(events: EventSystem, client: Client, token: String) {
        val api =
                Fuel.Companion.get("https://api.github.com/user").header(Pair("Authorization", "token $token")).responseString()
        val jsonResult = mapper.readTree(api.third.get())

        val loginName = jsonResult.get("login").asText()
        logger.info("$client with token $token is https://github.com/$loginName")
        client.name = loginName
        events.execute(ClientLoginEvent(client, loginName, "github"))
    }

    fun register(events: EventSystem) {
        events.addListener(ClientJsonMessage::class, {
            if (it.data.getTextOrDefault("type", "") == "Auth") {
                if (it.data.getTextOrDefault("provider", "") == "github") {
                    fetchGithubUser(events, it.client, it.data.getTextOrDefault("token", ""))
                }
            }
        })
        events.addListener(ClientJsonMessage::class, {
            if (it.data.getTextOrDefault("type", "") == "Auth") {
                if (it.data.getTextOrDefault("provider", "") == "guest") {
                    fetchGuestUser(events, it.client)
                }
            }
        })

        events.addListener(ClientLoginEvent::class, {
            it.client.send(mapper.createObjectNode().put("type", "Auth").put("name", it.loginName))
        })
    }

}