package net.zomis.games.compose.common

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.doOnCreate
import com.arkivanov.essenty.lifecycle.doOnStart
import io.ktor.client.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.zomis.games.compose.common.network.ClientConnection
import net.zomis.games.compose.common.network.Message
import net.zomis.games.server2.ServerGames

interface HomeComponent {
    val player: Value<Message.AuthMessage>
    val lobby: Value<Message.LobbyMessage>
}

class DefaultHomeComponent(componentContext: ComponentContext, connection: ClientConnection) : HomeComponent {
    private val coroutineScope = CoroutineScope(Dispatchers.Default, componentContext.lifecycle)
    override val lobby = MutableValue(Message.LobbyMessage(emptyMap()))
    override val player: Value<Message.AuthMessage> = MutableValue(connection.auth!!)

    init {
        componentContext.lifecycle.doOnCreate {
            coroutineScope.launch {
                connection.joinLobby(
                    ServerGames.games.keys, maxGames = 10
                )
                lobby.value = connection.updateLobby()
            }
        }
        componentContext.lifecycle.doOnStart {
            coroutineScope.launch {
                lobby.value = connection.updateLobby()
            }
        }
    }

}

@Composable
fun HomeContent(component: HomeComponent) {
    val player = component.player.subscribeAsState().value
    val lobby = component.lobby.subscribeAsState().value.users.entries.toList()
    Column {
        TopAppBar {
            Text(text = player.name)
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(300.dp)
        ) {
            items(items = lobby, key = { it.key }) { lobbyGame ->
                Card(
                    Modifier.padding(32.dp).fillMaxWidth()
                ) {
                    Text(lobbyGame.key)
                }
            }
        }
    }

}

@Preview
@Composable
fun HomePreview() {
    val component = object : HomeComponent {
        override val player: Value<Message.AuthMessage> = MutableValue(
            Message.AuthMessage(playerId = "abc", name = "Test", picture = "https://avatars.githubusercontent.com/u/1405379?v=4", cookie = null)
        )
        override val lobby: Value<Message.LobbyMessage> = MutableValue(
            Message.LobbyMessage(
                mapOf(
                    "SomeGame" to TestData.playerInfoList,
                    "SomeOtherGame" to TestData.playerInfoList
                )
            )
        )
    }
    HomeContent(component)

}