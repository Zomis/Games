package net.zomis.games.compose.common

import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext
import io.ktor.client.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.zomis.games.compose.common.network.ClientConnection
import kotlin.coroutines.CoroutineContext



interface LoginComponent {
    val localStorage: LocalStorage
    val onConnected: (ClientConnection) -> Unit
    fun gitHubLogin()
}

class DefaultLoginComponent(
    componentContext: ComponentContext,
    context: CoroutineContext,
    private val httpClient: HttpClient,
    override val localStorage: LocalStorage,
    override val onConnected: (ClientConnection) -> Unit,
) : ComponentContext by componentContext, LoginComponent {

    private val coroutineScope = CoroutineScope(context, componentContext.lifecycle)

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
        ClientConnection.connectWebSocket(httpClient, coroutineScope, "wss://games.zomis.net/backend/websocket") {
            it.auth(provider, token)
            onConnected.invoke(it)
        }
    }

}

@Composable
fun LoginContent(component: DefaultLoginComponent) {
    Button({
        component.gitHubLogin()
    }) {
        Text("Connect with GitHub")
    }

    // Open browser, use code callback
    // Start local HTTP server and use as redirect URI

}
