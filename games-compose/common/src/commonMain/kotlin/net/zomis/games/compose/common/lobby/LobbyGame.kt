package net.zomis.games.compose.common.lobby

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.zomis.games.compose.common.Configuration
import net.zomis.games.compose.common.Navigator
import net.zomis.games.compose.common.NoopNavigator
import net.zomis.games.compose.common.TestData
import net.zomis.games.server2.invites.PlayerInfo

@Composable
fun LobbyGame(gameType: String, players: List<PlayerInfo>, onCreateInvite: (String) -> Unit) {
    Card(
        Modifier.padding(32.dp).fillMaxWidth().padding(16.dp)
    ) {
        Column {
            Text(gameType)
            // TODO: Image / Example game. See https://github.com/Zomis/Games/issues/101
            // TODO: Description. See https://github.com/Zomis/Games/issues/101

            // Accordion, expand button, animated size/visibility.
            // Create invite button
            Text(text = players.map { it.name }.toString())

            Button(onClick = {
                // Switch to "Create invite" screen / show dialog
                onCreateInvite.invoke(gameType)
            }) {
                Text("Create invite")
            }
        }
    }
}

@Composable
@Preview
private fun LobbyGamePreview() {
    LobbyGame("Test", TestData.playerInfoList.take(4)) {}
}
