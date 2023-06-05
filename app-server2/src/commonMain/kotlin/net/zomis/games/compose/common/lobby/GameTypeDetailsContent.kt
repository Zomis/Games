package net.zomis.games.compose.common.lobby

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import net.zomis.games.compose.common.gametype.GameTypeDetails

@Composable
fun GameTypeDetailsContent(
    gameTypeDetails: GameTypeDetails,
    showDescription: Boolean = false,
    showPreview: Boolean = false,
    showLinks: Boolean = false,
    showTags: Boolean = false,
) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = gameTypeDetails.name)
        Text(text = gameTypeDetails.playersCount.first.toString() + ".." + gameTypeDetails.playersCount.last)
        if (showDescription) {
            Text(text = gameTypeDetails.description)
        }
        if (showPreview) {
            Text("TODO: Show Preview")
        }
    }
}
