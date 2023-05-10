package net.zomis.games.compose.common.lobby

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.zomis.games.compose.common.TestData
import net.zomis.games.server2.invites.PlayerInfo

@Composable
fun LobbyGame(gameType: String, players: List<PlayerInfo>, inviteStore: InvitationsStore) {
    Card(
        Modifier.padding(32.dp).fillMaxWidth()
    ) {
        Column {
            Text(gameType)
            Text(text = players.map { it.name }.toString())
        }
    }
}

@Composable
@Preview
private fun LobbyGamePreview() {
    LobbyGame("Test", TestData.playerInfoList.take(4), InvitationStoreEmpty())
}
