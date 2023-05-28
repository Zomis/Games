package net.zomis.games.compose.common.server2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.ComponentContext
import io.ktor.client.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.zomis.games.compose.common.ClientConfig
import net.zomis.games.compose.common.CoroutineScope
import net.zomis.games.compose.common.LocalStorage
import net.zomis.games.compose.common.OAuth
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
    private val clientConfig: ClientConfig,
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
        ClientConnection.connectWebSocket(httpClient, coroutineScope, clientConfig.websocketUrl) {
            it.auth(provider, token)
            onConnected.invoke(it)
        }
    }

}

@Composable
fun LoginContent(component: LoginComponent) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Button({
            component.gitHubLogin()
        }) {
            Text("Connect with GitHub")
        }
    }

    // Open browser, use code callback
    // Start local HTTP server and use as redirect URI

}
