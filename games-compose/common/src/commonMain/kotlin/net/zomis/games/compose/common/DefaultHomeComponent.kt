package net.zomis.games.compose.common

import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnCreate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.zomis.games.compose.common.network.ClientConnection
import net.zomis.games.server2.ServerGames

class DefaultHomeComponent(componentContext: ComponentContext, connection: ClientConnection) {
    private val coroutineScope = CoroutineScope(Dispatchers.Default, componentContext.lifecycle)

    init {
        componentContext.lifecycle.doOnCreate {
            coroutineScope.launch {
                connection.joinLobby(
                    ServerGames.games.keys, maxGames = 10
                )
            }
        }
    }

}

@Composable
fun HomeContent(component: DefaultHomeComponent) {
    Button({

    }) {
        Text("This is the lobby")
    }

}
