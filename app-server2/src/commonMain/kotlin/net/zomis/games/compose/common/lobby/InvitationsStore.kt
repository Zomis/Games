package net.zomis.games.compose.common.lobby

import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.getAndUpdate
import com.arkivanov.decompose.value.operator.map
import com.arkivanov.decompose.value.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import net.zomis.games.compose.server2.Configuration
import net.zomis.games.compose.common.Navigator
import net.zomis.games.compose.common.network.ClientConnection
import net.zomis.games.compose.common.network.ClientToServerMessage
import net.zomis.games.compose.common.network.Message
import net.zomis.games.server2.invites.PlayerInfo

interface InvitationsStore {

    val invitations: Value<Map<String, Message.InviteView>>
    fun inviteRespond(inviteId: String, accept: Boolean)
    val allPlayers: Value<Map<String, List<PlayerInfo>>>

}

class InvitationStoreEmpty : InvitationsStore {
    override val invitations: Value<Map<String, Message.InviteView>> = MutableValue(emptyMap())
    override fun inviteRespond(inviteId: String, accept: Boolean) {}
    override val allPlayers: Value<Map<String, List<PlayerInfo>>> = MutableValue(emptyMap())
}

class InvitationsStoreImpl(
    private val scope: CoroutineScope,
    private val connection: ClientConnection,
    private val navigator: Navigator<Configuration>,
    private val player: Message.AuthMessage,
    override val allPlayers: Value<Map<String, List<PlayerInfo>>>,
) : InvitationsStore {
    override val invitations: MutableValue<Map<String, Message.InviteView>> = MutableValue(emptyMap())
    override fun inviteRespond(inviteId: String, accept: Boolean) {
        scope.launch {
            connection.send(ClientToServerMessage.InviteRespond(inviteId, accept))
        }
    }

    init {
        scope.launch {
            connection.messages.filterIsInstance<Message.InviteView>().collect { invite ->
                println("Update invitations $invite")
                val oldInvites = invitations.getAndUpdate { map ->
                    map + (invite.inviteId to invite)
                }
                if (oldInvites[invite.inviteId] == null && player.playerId in invite.players.map { it.id }) {
                    // val playerIsHost = invite.host.playerId == invite.host.playerId
                    val playerList = allPlayers.map { it[invite.gameType] ?: emptyList() }
                    navigator.navigateTo(Configuration.ViewInvite(invite, playerList, connection))
                }
            }
        }
//        scope.launch {
//             Maybe ask for inviteView when these come?
//            connection.messages.filterIsInstance<Message.Invite>()
//            connection.messages.filterIsInstance<Message.InviteResponse>()
//            connection.messages.filterIsInstance<Message.InviteStatus>()
//        }
        scope.launch {
            connection.messages.filterIsInstance<Message.InviteCancelled>().collect { invite ->
                println("Update invitations - remove $invite")
                invitations.update { map ->
                    map.toMutableMap().also { it.remove(invite.inviteId) }
                }
            }
        }
    }

}