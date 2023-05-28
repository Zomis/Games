package net.zomis.games.server2.steam

import io.ktor.client.*
import io.ktor.client.request.*
import kotlinx.coroutines.runBlocking
import net.zomis.games.server2.ClientJsonMessage

private const val APP_ID = "2431860"

class Steam(
    private val httpClient: HttpClient,
    private val apiKey: String,
    private val apiPublisherKey: String,
) {

    fun authUser(client: ClientJsonMessage) {
        runBlocking {
            httpClient.get("https://partner.steam-api.com/ISteamUserAuth/AuthenticateUserTicket/v1/") {
                header("x-webapi-key", apiKey)
                parameter("key", apiPublisherKey)
                parameter("appid", APP_ID)
                parameter("ticket", client.data.get("token").textValue())
            }
        }
    }

}