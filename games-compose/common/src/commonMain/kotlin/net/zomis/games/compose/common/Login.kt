package net.zomis.games.compose.common

import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

interface LoginComponent

private data class GitHubStepOneResponse(val device_code: String, val user_code: String, val verification_uri: String, val expires_in: Int, val interval: Int)
private data class GitHubAccessToken(val access_token: String, val token_type: String, val scope: String)

class DefaultLoginComponent(
    componentContext: ComponentContext,
    private val httpClient: HttpClient,
) : ComponentContext by componentContext {

    private val coroutineScope = CoroutineScope(Dispatchers.Default, componentContext.lifecycle)

    fun gitHubLogin() {
        coroutineScope.launch(Dispatchers.IO) {
            val response = httpClient.post("https://github.com/login/device/code") {
                header("Accept", "application/json")
                parameter("client_id", "2388ed73e48da00d9894")
                parameter("scope", "read:user")
            }.body<GitHubStepOneResponse>()
            println(response)
            delay(response.interval.seconds * 5)

            val accessTokenResponse = httpClient.post("https://github.com/login/oauth/access_token") {
                parameter("client_id", "2388ed73e48da00d9894")
                parameter("device_code", response.device_code)
                parameter("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
            }.body<String>()
            println(accessTokenResponse)
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
