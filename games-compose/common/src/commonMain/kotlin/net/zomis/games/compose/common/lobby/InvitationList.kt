package net.zomis.games.compose.common.lobby

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import net.zomis.games.compose.common.server2.GameTypeLink
import net.zomis.games.compose.common.PlayerProfile

@Composable
fun InvitationList(inviteStore: InvitationsStore) {

    val invites = inviteStore.invitations.subscribeAsState()

    LazyColumn {
        items(invites.value.entries.toList(), key = { it.key }) {
            val invite = it.value
            Column {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    PlayerProfile(invite.host.toPlayerInfo(), modifier = Modifier.weight(0.2f))
                    GameTypeLink(invite.gameType, modifier = Modifier.weight(0.2f))
                    Text(text = "${invite.players.size} / ${invite.minPlayers}..${invite.maxPlayers}", modifier = Modifier.weight(0.2f))
                    Row(modifier = Modifier.weight(0.2f)) {
                        Button(onClick = { inviteStore.inviteRespond(invite.inviteId, accept = true) }) {
                            Text(text = "Accept")
                        }
                        Button(onClick = { inviteStore.inviteRespond(invite.inviteId, accept = false) }) {
                            Text(text = "Decline")
                        }
                    }
                    // Text("public / invite-only")
                }
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    invite.players.forEachIndexed { index, player ->
                        key(player.id, index) {
                            PlayerProfile(player.asPlayerInfo(), modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

}