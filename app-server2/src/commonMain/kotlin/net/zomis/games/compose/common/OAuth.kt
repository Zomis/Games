package net.zomis.games.compose.common

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

class OAuth {
    object GitHub {
        private data class GitHubStepOneResponse(val device_code: String, val user_code: String, val verification_uri: String, val expires_in: Int, val interval: Int)
        private data class GitHubAccessToken(
            val access_token: String?,
            val token_type: String?,
            val scope: String?,
            val error: String?,
            val error_description: String?,
            val error_uri: String?
        )

        const val ClientId = "ec9c694603f523bc6de8"

        suspend fun gitHubConnect(httpClient: HttpClient): String {
            val response = httpClient.post("https://github.com/login/device/code") {
                header("Accept", "application/json")
                parameter("client_id", ClientId)
                parameter("scope", "read:user")
            }.body<GitHubStepOneResponse>()
            println(response)

            var accessTokenResponse: GitHubAccessToken
            do {
                delay(response.interval.seconds)

                accessTokenResponse = httpClient.post("https://github.com/login/oauth/access_token") {
                    parameter("client_id", ClientId)
                    parameter("device_code", response.device_code)
                    parameter("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                }.body()
            } while (accessTokenResponse.error != null)
            return accessTokenResponse.access_token!!
        }


    }


}