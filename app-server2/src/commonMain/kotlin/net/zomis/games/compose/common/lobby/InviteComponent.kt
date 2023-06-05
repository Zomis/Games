package net.zomis.games.compose.common.lobby

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.operator.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import net.zomis.games.compose.common.CoroutineScope
import net.zomis.games.compose.common.gametype.GameTypeDetails
import net.zomis.games.compose.common.network.ClientConnection
import net.zomis.games.compose.common.network.ClientToServerMessage
import net.zomis.games.compose.common.network.Message
import net.zomis.games.server2.invites.PlayerInfo
import net.zomis.games.server2.invites.PlayerInfoId

interface InviteComponent {

    val onStartGame: () -> Unit
    val onInvite: (PlayerInfo) -> Unit
    val showHostOptions: Boolean
    val isHost: Boolean
    val player: Value<Message.AuthMessage>
    val invite: Value<Message.InviteView>
    val availablePlayers: Value<List<PlayerInfo>>
    val gameTypeDetails: GameTypeDetails
    /*
    * game type + details
    * generic game options (read only)
    * game-specific options (read only)
    *
    * players in game / invites pending
    *
    * HOST ONLY:
    * list of people to invite (with search filter)
    */

}

class DefaultViewInviteComponent(
    componentContext: ComponentContext,
    private val connection: ClientConnection,
    override val availablePlayers: Value<List<PlayerInfo>>,
    override val gameTypeDetails: GameTypeDetails,
    invite: Message.InviteView,
) : InviteComponent {
    private val scope = CoroutineScope(Dispatchers.Default, componentContext.lifecycle)
    override val player: Value<Message.AuthMessage> = MutableValue(connection.auth!!)
    private val _invite = MutableValue(invite)
    override val invite: Value<Message.InviteView> = _invite
    override val isHost: Boolean = invite.host.playerId == player.value.playerId
    override val showHostOptions: Boolean = isHost

    override val onInvite: (PlayerInfo) -> Unit = {
        scope.launch {
            connection.send(ClientToServerMessage.InviteSend(invite.inviteId, listOf(it.playerId)))
        }
    }
    override val onStartGame: () -> Unit = {
        scope.launch {
            connection.send(ClientToServerMessage.InviteStart(invite.inviteId))
        }
    }

    init {
        scope.launch {
            connection.messages.filterIsInstance<Message.InviteView>().filter { it.inviteId == invite.inviteId }.collect {
                _invite.value = it
            }
        }
    }

}

@Composable
fun InviteContent(component: InviteComponent) {
    val players = component.invite.map { it.players }.subscribeAsState()
    val invitedPlayers = component.invite.map { it.invited.map(PlayerInfoId::toPlayerInfo) }.subscribeAsState()
    val invitablePlayers = component.availablePlayers.subscribeAsState()
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(300.dp)) {
            Card {
                GameTypeDetailsContent(component.gameTypeDetails)
            }
            Card {
                // Generic Game Options
            }
            Card {
                // Game-specific options
            }
        }
        Column(Modifier.weight(0.5f)) {
            Row(Modifier.fillMaxHeight()) {
                Card(Modifier.weight(0.5f)) {
                    // People in game + Invites pending
                    LazyColumn {
                        itemsIndexed(
                            items = players.value,
                            key = { index, item -> "$index,${item.id}" }
                        ) { index, player ->
                            PlayerView.InvitedAccepted(index, player.asPlayerInfo(), component.showHostOptions)
                        }
                    }
                    LazyColumn {
                        itemsIndexed(
                            items = invitedPlayers.value,
                            key = { index, item -> "$index,${item.playerId}" }
                        ) { index, player ->
                            PlayerView.Invited(index, player, component.showHostOptions)
                        }
                    }
                }
                if (component.showHostOptions) {
                    Card(Modifier.weight(0.5f)) {
                        LazyColumn {
                            items(
                                items = invitablePlayers.value,
                                key = { it.playerId }
                            ) {
                                PlayerView.Inviteable(it, component.onInvite)
                            }
                        }
                        component.availablePlayers.value
                        // List of people to invite (with search filter)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.End) {
                Button(onClick = { }) {
                    if (component.isHost) {
                        Text("Cancel")
                    } else {
                        Text("Leave")
                    }
                }
                Button(onClick = component.onStartGame) {
                    Text("Start Game")
                }
            }
        }
    }
}
