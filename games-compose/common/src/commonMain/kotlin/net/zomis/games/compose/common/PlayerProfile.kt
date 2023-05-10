package net.zomis.games.compose.common

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.zomis.games.server2.invites.PlayerInfo

@Composable
fun PlayerProfile(playerInfo: PlayerInfo, modifier: Modifier = Modifier) {
    Text(text = playerInfo.name ?: "(unknown name)", modifier = modifier)
}
