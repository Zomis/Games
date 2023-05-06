package net.zomis.games.compose.common

import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

interface LoginComponent {

    fun gitHubLogin()
}

private data class GitHubStepOneResponse(val device_code: String, val user_code: String, val verification_uri: String, val expires_in: Int, val interval: Int)
private data class GitHubAccessToken(
    val access_token: String?,
    val token_type: String?,
    val scope: String?,
    val error: String?,
    val error_description: String?,
    val error_uri: String?
)

class DefaultLoginComponent(
    componentContext: ComponentContext,
    private val httpClient: HttpClient,
) : ComponentContext by componentContext, LoginComponent {
    private val mapper = jacksonObjectMapper()
    val connected = MutableValue(false)

    private val coroutineScope = CoroutineScope(Dispatchers.Default, componentContext.lifecycle)

    override fun gitHubLogin() {
        coroutineScope.launch(Dispatchers.IO) {
            val response = httpClient.post("https://github.com/login/device/code") {
                header("Accept", "application/json")
                parameter("client_id", OAuth.GitHub.ClientId)
                parameter("scope", "read:user")
            }.body<GitHubStepOneResponse>()
            println(response)

            var accessTokenResponse: GitHubAccessToken
            do {
                delay(response.interval.seconds)

                accessTokenResponse = httpClient.post("https://github.com/login/oauth/access_token") {
                    parameter("client_id", OAuth.GitHub.ClientId)
                    parameter("device_code", response.device_code)
                    parameter("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                }.body()
            } while (accessTokenResponse.error != null)


            httpClient.webSocket("wss://games.zomis.net/backend/websocket") {
                connected.value = true
                send("""{ "route": "auth/github", "token": "${accessTokenResponse.access_token}" }""")
                send("""{ "route": "lobby/join", "gameTypes": ["NoThanks"], "maxGames": 10 }""")
                send("""{ "route": "lobby/list" }""")
                for (frame in incoming) {
                    when (frame) {
                        is Frame.Text -> {
                            println(frame.readText())
                        }
                        is Frame.Close -> {
                            println("Close reason: " + frame.readReason())
                            connected.value = false
                        }
                        else -> {}
                    }
                }
                send("")
            }
        }
    }

}

@Composable
fun LoginContent(component: DefaultLoginComponent) {
    Button({
        // https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/authorizing-oauth-apps#device-flow
        /*
        * POST https://github.com/login/device/code
        * client_id
        * scope=read:user
        *
        **/
        component.gitHubLogin()



    }) {
        Text("Connect with GitHub")
    }

    // Open browser, use code callback
    // Start local HTTP server and use as redirect URI

}
