package net.zomis.games.compose.common

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.arkivanov.essenty.lifecycle.doOnCreate
import com.arkivanov.essenty.lifecycle.doOnStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import net.zomis.games.compose.common.lobby.InvitationList
import net.zomis.games.compose.common.lobby.InvitationStoreEmpty
import net.zomis.games.compose.common.lobby.InvitationsStore
import net.zomis.games.compose.common.lobby.LobbyGame
import net.zomis.games.compose.common.network.ClientConnection
import net.zomis.games.compose.common.network.Message
import net.zomis.games.server2.ServerGames

interface HomeComponent {
    val player: Value<Message.AuthMessage>
    val lobby: Value<Message.LobbyMessage>
    val invites: InvitationsStore
    val lobbyChangeMessages: Flow<Message.LobbyChangeMessage>
}

class DefaultHomeComponent(
    componentContext: ComponentContext,
    connection: ClientConnection,
    override val invites: InvitationsStore
) : HomeComponent {
    private val coroutineScope = CoroutineScope(Dispatchers.Default, componentContext.lifecycle)
    override val lobby = MutableValue(Message.LobbyMessage(emptyMap()))
    override val player: Value<Message.AuthMessage> = MutableValue(connection.auth!!)
    override val lobbyChangeMessages: Flow<Message.LobbyChangeMessage> = connection.messages.filterIsInstance()

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
                lobbyChangeMessages.collect { lobbyChange ->
                    if (lobbyChange.didJoin()) {
                        val joinedGames = lobbyChange.gameTypes!!.toSet()
                        lobby.update { oldLobby ->
                            oldLobby.copy(
                                users = oldLobby.users.mapValues {
                                    if (it.key in joinedGames) {
                                        it.value + lobbyChange.player
                                    } else {
                                        it.value
                                    }
                                }
                            )
                        }
                    } else {
                        lobby.update { oldLobby ->
                            oldLobby.copy(
                                users = oldLobby.users.mapValues { oldUsers -> oldUsers.value.filter { it.playerId != lobbyChange.player.playerId } }
                            )
                        }
                    }
                }
            }
        }
    }

}

@Composable
fun HomeContent(component: HomeComponent) {
    val lobby = component.lobby.subscribeAsState().value.users.entries.toList()
    AppView(component.player) {
        InvitationList(component.invites)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(300.dp)
        ) {
            items(items = lobby, key = { it.key }) { lobbyGame ->
                LobbyGame(lobbyGame.key, lobbyGame.value, component.invites)
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
        override val invites: InvitationsStore = InvitationStoreEmpty()
        override val lobbyChangeMessages: Flow<Message.LobbyChangeMessage> = emptyFlow()
    }
    HomeContent(component)

}