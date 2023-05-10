package net.zomis.games.compose.common.lobby

import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import net.zomis.games.compose.common.network.ClientConnection
import net.zomis.games.compose.common.network.ClientToServerMessage
import net.zomis.games.compose.common.network.Message

interface InvitationsStore {

    val invitations: Value<Map<String, Message.InviteView>>
    fun inviteRespond(inviteId: String, accept: Boolean)

}

class InvitationStoreEmpty : InvitationsStore {
    override val invitations: Value<Map<String, Message.InviteView>> = MutableValue(emptyMap())
    override fun inviteRespond(inviteId: String, accept: Boolean) {}
}

class InvitationsStoreImpl(private val scope: CoroutineScope, private val connection: ClientConnection) : InvitationsStore {
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
                invitations.update { map ->
                    map + (invite.inviteId to invite)
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