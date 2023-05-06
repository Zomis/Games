package net.zomis.games.compose.common

import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.zomis.games.server2.ServerGames

interface LoginComponent {
    val localStorage: LocalStorage
    fun gitHubLogin()
}

class DefaultLoginComponent(
    componentContext: ComponentContext,
    private val httpClient: HttpClient,
    override val localStorage: LocalStorage,
) : ComponentContext by componentContext, LoginComponent {
    private val mapper = jacksonObjectMapper()
    val connected = MutableValue(false)

    private val coroutineScope = CoroutineScope(Dispatchers.Default, componentContext.lifecycle)

    override fun gitHubLogin() {
        coroutineScope.launch(Dispatchers.IO) {
            val token = localStorage.loadOptional("cookie", String::class)
            if (token != null) {
                serverConnect("github", token)
            } else {
                val accessToken = OAuth.GitHub.gitHubConnect(httpClient)
                localStorage.save("cookie", accessToken)
                serverConnect("github", accessToken)
            }
        }
    }

    private suspend fun serverConnect(provider: String, token: String) {
        httpClient.webSocket("wss://games.zomis.net/backend/websocket") {
            connected.value = true
            send("""{ "route": "auth/$provider", "token": "$token" }""")
            val gameKeys = mapper.writeValueAsString(ServerGames.games.keys)
            send("""{ "route": "lobby/join", "gameTypes": $gameKeys, "maxGames": 10 }""")
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
