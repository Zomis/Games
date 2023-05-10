package net.zomis.games.compose.common

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun GameTypeLink(gameType: String, modifier: Modifier = Modifier) {
    Text(text = gameType, modifier = modifier)
}
