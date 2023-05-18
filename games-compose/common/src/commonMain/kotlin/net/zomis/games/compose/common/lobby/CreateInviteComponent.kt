package net.zomis.games.compose.common.lobby

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import net.zomis.games.compose.common.gametype.GameTypeDetails
import net.zomis.games.compose.common.network.Message

class GenericGameOptions {
    fun toServerOptions(): Any = emptyMap<String, Unit>()

    /*
    * store in database
    * time limit
    * private/public invite
    * allow suggested invites(?)
    * desired player count
    * starting player
    */
}

class PreparedInvite(
    val gameType: String,
    val genericGameOptions: GenericGameOptions,
    val gameSpecificOptions: Any,
)

interface CreateInviteComponent {
    val invitePrepare: Message.InvitePrepare
    val gameDetails: GameTypeDetails
    val genericGameOptions: Value<GenericGameOptions>
    val gameSpecificOptions: Value<Any>
    fun createInvite()

/*
* game type + details
*   live game played
* generic game options
* game-specific options
*/

}

class DefaultCreateInviteComponent(
    componentContext: ComponentContext,
    override val invitePrepare: Message.InvitePrepare,
    override val gameDetails: GameTypeDetails,
    private val onCreateInvite: (PreparedInvite) -> Unit,
) : CreateInviteComponent {
    override val gameSpecificOptions: Value<Any> = MutableValue(invitePrepare.config ?: Unit)
    override val genericGameOptions: Value<GenericGameOptions> = MutableValue(GenericGameOptions())

    override fun createInvite() {
        val serializedGameOptions = gameSpecificOptions.value.takeIf { it !is Unit } ?: emptyMap<String, Any>()
        onCreateInvite.invoke(
            PreparedInvite(
                gameType = invitePrepare.gameType,
                genericGameOptions = genericGameOptions.value,
                gameSpecificOptions = serializedGameOptions,
            )
        )
    }
}

@Composable
fun CreateInviteContent(component: CreateInviteComponent) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.weight(0.5f).padding(12.dp)) {
            // Game Details
            Card(Modifier.weight(0.5f).background(Color.Magenta)) {
                GameTypeDetailsContent(component.gameDetails, showDescription = true, showPreview = true, showLinks = true, showTags = true)
            }

            // Generic Game Options
            Card(Modifier.weight(0.5f).background(Color.Green)) {

            }
        }
        Card(Modifier.weight(0.5f)) {
            // Game-specific options
        }
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.End) {
            Button(onClick = {}, modifier = Modifier.padding(end = 12.dp)) {
                Text("Cancel")
            }
            Button(onClick = {
                component.createInvite()
            }) {
                Text("Next")
            }

        }
    }


}