package net.zomis.games.server2

import com.codedisaster.steamworks.*
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.serialization.jackson.*
import kotlinx.coroutines.runBlocking
import net.zomis.core.events.EventSystem
import net.zomis.games.server2.steam.Steam
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
        .map { it[0].uppercase() + it.substring(1) }
}

class AuthorizationCallback(
    val cookieLookup: (String) -> PlayerDatabaseInfo? = { null }
)
data class PlayerDatabaseInfo(val name: String, val playerId: PlayerId)

class AuthorizationSystem(
    private val events: EventSystem,
    private val steam: Steam? = null,
    httpClientFactory: () -> HttpClient = { HttpClient() },
    private val callback: AuthorizationCallback = AuthorizationCallback(),
) {
    private val secureRandom: SecureRandom = SecureRandom()
    val router = MessageRouter(this)
        .handler("guest", this::handleGuest)
        .handler("github", this::handleGitHub)
        .handler("google", this::handleGoogle)
        .handler("steam", this::handleSteam)
    private val httpClient = httpClientFactory.invoke()

    private val logger = LoggerFactory.getLogger(AuthorizationSystem::class.java)
    private val mapper = ObjectMapper()

    private val guestRandom = kotlin.random.Random.Default
    private val guestAdjectives = this.javaClass.classLoader.getResource("lists/adjectives.txt")?.lines()
    private val guestAnimals = this.javaClass.classLoader.getResource("lists/animals.txt")?.lines()

    private fun handleGuest(message: ClientJsonMessage) {
        val client = message.client
        var cookie = message.data["token"].asText()
        if (cookie.startsWith("cookie:")) {
            cookie = cookie.substringAfterLast("cookie:")
            val playerInfo = callback.cookieLookup(cookie)
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
        val jsonResult = runBlocking {
            httpClient.get("https://api.github.com/user") {
                header("Authorization", "token $token")
            }.body<ObjectNode>()
        }

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

        val result = runBlocking {
            httpClient.get("https://www.googleapis.com/userinfo/v2/me") {
                header("Authorization", "Bearer $token")
            }
        }
        logger.info(result.toString())

        val tree = runBlocking {
            result.body<ObjectNode>()
        }

        val providerName = tree.get("given_name").asText() + " " + tree.get("family_name").asText()
        val avatarUrl = tree.get("picture").asText()

        val id = tree.get("id").asText()

        logger.info("$client with token $token is google/$id/$providerName")
        client.updateInfo(providerName, UUID.randomUUID(), avatarUrl)
        events.execute(ClientLoginEvent(client, id, providerName, "github", token))
        sendLoginToClient(client)
    }

    private fun handleSteam(message: ClientJsonMessage) {
        if (steam != null) {
            steam.authUser(message)
            return
        }

        val client = message.client
        val token = message.data.getTextOrDefault("token", "")



        val server = SteamGameServer(object : SteamGameServerCallback {
            override fun onValidateAuthTicketResponse(
                steamID: SteamID?,
                authSessionResponse: SteamAuth.AuthSessionResponse?,
                ownerSteamID: SteamID?
            ) {}

            override fun onSteamServersConnected() {}

            override fun onSteamServerConnectFailure(result: SteamResult?, stillRetrying: Boolean) {}

            override fun onSteamServersDisconnected(result: SteamResult?) {}

            override fun onClientApprove(steamID: SteamID?, ownerSteamID: SteamID?) {}

            override fun onClientDeny(
                steamID: SteamID?,
                denyReason: SteamGameServer.DenyReason?,
                optionalText: String?
            ) {}

            override fun onClientKick(steamID: SteamID?, denyReason: SteamGameServer.DenyReason?) {}

            override fun onClientGroupStatus(
                steamID: SteamID?,
                steamIDGroup: SteamID?,
                isMember: Boolean,
                isOfficer: Boolean
            ) {}

            override fun onAssociateWithClanResult(result: SteamResult?) {}

            override fun onComputeNewPlayerCompatibilityResult(
                result: SteamResult?,
                playersThatDontLikeCandidate: Int,
                playersThatCandidateDoesntLike: Int,
                clanPlayersThatDontLikeCandidate: Int,
                steamIDCandidate: SteamID?
            ) {}
        })

//        server.beginAuthSession()
        
//        server.cancelAuthTicket()


        val result = runBlocking {
            httpClient.get("https://www.googleapis.com/userinfo/v2/me") {
                header("Authorization", "Bearer $token")
            }
        }
        logger.info(result.toString())

        val tree = runBlocking {
            result.body<ObjectNode>()
        }

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
