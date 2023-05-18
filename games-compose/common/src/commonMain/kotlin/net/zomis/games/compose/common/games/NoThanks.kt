package net.zomis.games.compose.common.games

import androidx.compose.foundation.layout.Row
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import net.zomis.games.compose.common.game.GameClient
import net.zomis.games.impl.NoThanks

@Composable
fun NoThanksGameView(viewModel: NoThanks.ViewModel, gameClient: GameClient) {
    // Button - No Thanks
    // Button - Yes Please

    // Show view model

    Row {
        Text(viewModel.currentCard.toString())
        Button(onClick = {
            gameClient.postAction(NoThanks.action.name, false)
        }) {
            Text("No Thanks!")
        }

        Button(onClick = {
            gameClient.postAction(NoThanks.action.name, true)
        }) {
            Text("Yes Please!")
        }
    }
}
