package net.zomis.games.compose.common.games

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import net.zomis.games.compose.common.PlayerImage
import net.zomis.games.compose.common.game.GameClient
import net.zomis.games.impl.NoThanks
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun NoThanksGameView(viewModel: NoThanks.ViewModel, gameClient: GameClient) {
    Box(Modifier.fillMaxSize().padding(16.dp)) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Row {
                Box(Modifier.size(64.dp).background(Color.LightGray), contentAlignment = Alignment.Center) {
                    Text(text = viewModel.cardsRemaining.toString())
                }
                Box(Modifier.size(64.dp).background(Color.Black), contentAlignment = Alignment.Center) {
                    Text(text = viewModel.currentCard.toString(), color = Color.White)
                }
            }
            Row {
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
        val players = gameClient.players.subscribeAsState()
        for ((index, player) in players.value.withIndex()) {
            val angle = (index.toFloat() / gameClient.playerCount) * PI * 2 + PI / 2
            val distance = 200.dp
            Card(Modifier.align(Alignment.Center).absoluteOffset(cos(angle) * distance, sin(angle) * distance)) {
                Column(Modifier.width(200.dp)) {
                    Row(Modifier.width(100.dp)) {
                        PlayerImage(player, Modifier.size(64.dp))
                        Text(player.name ?: "(unknown)")
                    }
                    Text(viewModel.players[index].tokens.toString())
                    Row(Modifier.width(100.dp)) {
                        for (i in viewModel.players[index].cards) {
                            Text(i.toString())
                        }
                    }
                }
            }

        }
    }
}
