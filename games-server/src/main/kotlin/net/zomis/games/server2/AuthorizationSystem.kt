package net.zomis.games.server2

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.kittinunf.fuel.Fuel
import net.zomis.core.events.EventSystem
import org.slf4j.LoggerFactory
import java.net.URL
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

data class ClientLoginEvent(
    val client: Client,
    val providerId: String,
    val loginName: String,
    val provider: String,
    @Deprecated("unused")
    val token: String
)

fun URL.lines(): List<String> {
    return this.readText(Charsets.UTF_8).split("\n")
        .map { it.trim() }
        .filter { it.isNotEmpty() }.distinct()
        .map { it[0].toUpperCase() + it.substring(1) }
}

class AuthorizationCallback(
    val cookieLookup: (String) -> PlayerDatabaseInfo? = { null }
)
data class PlayerDatabaseInfo(val name: String, val playerId: PlayerId)

class AuthorizationSystem(private val events: EventSystem, private val callback: AuthorizationCallback = AuthorizationCallback()) {
    private val secureRandom: SecureRandom = SecureRandom()
    val router = MessageRouter(this)
        .handler("guest", this::handleGuest)
        .handler("github", this::handleGitHub)
        .handler("google", this::handleGoogle)

    private val logger = LoggerFactory.getLogger(AuthorizationSystem::class.java)
    private val mapper = ObjectMapper()

    private val guestRandom = kotlin.random.Random.Default
    private val guestAdjectives = this.javaClass.classLoader.getResource("lists/adjectives.txt")?.lines()
    private val guestAnimals = this.javaClass.classLoader.getResource("lists/animals.txt")?.lines()

    private fun handleGuest(message: ClientJsonMessage) {
        val client = message.client
        val cookie = message.data["token"].asText()
        if (cookie.startsWith("cookie:")) {
            val playerInfo = callback.cookieLookup(cookie.substringAfterLast("cookie:"))
            if (playerInfo == null) {
                // No need to log client information as client is not logged in
                logger.info("Invalid cookie was sent: $cookie")
                return
            }

            client.updateInfo(playerInfo.name, playerInfo.playerId, null)
            events.execute(ClientLoginEvent(client, cookie, playerInfo.name, "guest", cookie))
            sendLoginToClient(client, cookie)
        } else {
            val token: String = guestAdjectives?.random()
                ?.plus(guestAnimals?.random()?:"")
                ?.plus(guestRandom.nextInt(10, 99))
                ?: guestRandom.nextInt(100000).toString()
            this.handleGuest(client, token, UUID.randomUUID(), this::generateSecureCookie)
        }
    }

    fun handleGuest(client: Client, token: String, uuid: UUID, cookieProvider: () -> String) {
        val loginName = token
        logger.info("$client with token (empty) is guest/$loginName")
        client.updateInfo(loginName, uuid)

        val cookie = cookieProvider()
        events.execute(ClientLoginEvent(client, cookie, loginName, "guest", token))
        sendLoginToClient(client, cookie)
    }

    private fun generateSecureCookie(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
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
        sendLoginToClient(client)
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
        sendLoginToClient(client)
    }

    private fun sendLoginToClient(client: Client, cookie: String? = null) {
        client.send(mapOf(
            "type" to "Auth", "playerId" to client.playerId,
            "name" to client.name,
            "picture" to client.picture
        ).let { if (cookie != null) it.plus("cookie" to cookie) else it })
    }

}
