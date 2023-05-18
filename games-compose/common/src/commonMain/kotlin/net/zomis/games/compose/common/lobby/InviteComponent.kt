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
import net.zomis.games.compose.common.network.ClientConnection
import net.zomis.games.compose.common.network.Message

interface InviteComponent {

    val player: Value<Message.AuthMessage>
    val invite: Value<Message.InviteView>
    val invites: InvitationsStore

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
) : InviteComponent {
    override val player: Value<Message.AuthMessage> = MutableValue(connection.auth!!)
    override val invite: Value<Message.InviteView>
        get() = TODO("Not yet implemented")
    override val invites: InvitationsStore
        get() = TODO("Not yet implemented")


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
                if (component.invite.value.host.playerId == component.player.value.playerId) {
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
