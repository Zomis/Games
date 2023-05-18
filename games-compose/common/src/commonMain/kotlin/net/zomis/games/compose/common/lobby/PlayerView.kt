package net.zomis.games.compose.common.lobby

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.zomis.games.compose.common.PlayerImage
import net.zomis.games.compose.common.PlayerProfile
import net.zomis.games.server2.invites.PlayerInfo

object PlayerView {

    @Composable
    fun Invited(index: Int, playerInfo: PlayerInfo, showHostOptions: Boolean) {
        Invited(index, playerInfo, showHostOptions, accepted = false)
    }

    @Composable
    fun InvitedAccepted(index: Int, playerInfo: PlayerInfo, showHostOptions: Boolean) {
        Invited(index, playerInfo, showHostOptions, accepted = true)
    }

    @Composable
    private fun Invited(
        index: Int,
        playerInfo: PlayerInfo,
        showHostOptions: Boolean,
        accepted: Boolean
    ) {
        Row(Modifier.fillMaxWidth()) {
            PlayerImage(playerInfo, modifier = Modifier.padding(end = 8.dp).size(64.dp))
            Text(text = playerInfo.name ?: "", modifier = Modifier.weight(1f))
            if (accepted) {
                Text(text = "✅", modifier = Modifier.padding(8.dp))
            } else {
                Text(text = "❓", modifier = Modifier.padding(8.dp))
            }
        }
    }

    @Composable
    fun Inviteable(playerInfo: PlayerInfo, onInvite: (PlayerInfo) -> Unit) {
        Row(Modifier.fillMaxWidth()) {
            PlayerImage(playerInfo, modifier = Modifier.padding(end = 8.dp).size(64.dp))
            Text(text = playerInfo.name ?: "", modifier = Modifier.weight(1f))
            Text(text = "➕", modifier = Modifier.padding(8.dp).clickable {
                onInvite.invoke(playerInfo)
            })
        }
    }

}