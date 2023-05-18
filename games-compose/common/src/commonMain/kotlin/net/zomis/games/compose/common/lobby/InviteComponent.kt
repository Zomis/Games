package net.zomis.games.compose.common.lobby

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import net.zomis.games.compose.common.CoroutineScope
import net.zomis.games.compose.common.network.ClientConnection
import net.zomis.games.compose.common.network.Message
import net.zomis.games.server2.invites.PlayerInfo

interface InviteComponent {

    val showHostOptions: Boolean
    val player: Value<Message.AuthMessage>
    val invite: Value<Message.InviteView>
    val availablePlayers: Value<List<PlayerInfo>>

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
    invite: Message.InviteView,
) : InviteComponent {
    override val player: Value<Message.AuthMessage> = MutableValue(connection.auth!!)
    private val _invite = MutableValue(invite)
    override val invite: Value<Message.InviteView> = _invite
    override val showHostOptions: Boolean = invite.host.playerId == player.value.playerId

    private val scope = CoroutineScope(Dispatchers.Default, componentContext.lifecycle)

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
    Row {
        Column {
            Card {
                Text(component.invite.value.gameType)
                // Game Name + Players + Time
            }
            Card {
                // Generic Game Options
            }
            Card {
                // Game-specific options
            }
        }
        Column {
            Row(Modifier.fillMaxHeight(1f)) {
                Card {
                    // People in game + Invites pending
                }
                if (component.showHostOptions) {
                    Card {
                        // List of people to invite (with search filter)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.End) {
                Button(onClick = {}) {
                    Text("Cancel")
                }
                Button(onClick = {}) {
                    Text("Start Game")
                }
            }
        }
    }
}
