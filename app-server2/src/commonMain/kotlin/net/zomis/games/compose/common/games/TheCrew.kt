package net.zomis.games.compose.common.games

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.unit.dp
import net.zomis.games.compose.common.game.GameClient
import net.zomis.games.impl.cards.TheCrew

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TheCrewGameView(viewModel: TheCrew.ViewModel, gameClient: GameClient) {
    Column {
        Text("You are viewing ${viewModel.viewer}")
        Text("Current Player: ${viewModel.currentPlayer}")

        Row {
            for (player in viewModel.players) {
                Column {
                    Text("Player ${player.playerIndex}")
                    TheCrewViewPlayer(player)
                }
            }
        }

        Row {
            Text("Current Trick:")
            val cards = viewModel.currentTrick?.cards ?: emptyList()
            for (i in cards) {
                CrewCardView(i)
            }
        }

        if (viewModel.yourHand != null) {
            Text("Your hand:")
            Row {
                for (c in viewModel.yourHand!!) {
                    CrewCardView(c, modifier = Modifier.onClick(matcher = PointerMatcher.Primary) {
                        gameClient.postAction("play", c)
                    }.onClick(matcher = PointerMatcher.mouse(button = PointerButton.Secondary)) {
                        val communicationType = viewModel.possibleCommunication(c) ?: return@onClick
                        gameClient.postAction("communicate", TheCrew.Communication(c, communicationType))
                    })
                }
            }
        }
    }
}

@Composable
private fun TheCrewViewPlayer(player: TheCrew.ViewPlayer) {
    Row {
        val communication = player.communication
        if (communication == null) {
            Text("No communication")
        } else {
            Text(communication.communicationType.name)
            CrewCardView(communication.card)
        }

        for (mission in player.missions) CrewCardView(mission)
    }
}

@Composable
private fun CrewCardView(card: TheCrew.SuitAndValue, modifier: Modifier = Modifier) {
    Box(
        modifier = Modifier.padding(2.dp).width(32.dp).height(48.dp).background(card.suit.backgroundColor).then(modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(text = card.value.toString(), color = card.suit.textColor)
    }
}

private val TheCrew.CardSuit.backgroundColor get() = when(this) {
    TheCrew.CardSuit.Yellow -> Color.Yellow
    TheCrew.CardSuit.Pink -> Color.Magenta
    TheCrew.CardSuit.Green -> Color.Green
    TheCrew.CardSuit.Blue -> Color.Cyan
    TheCrew.CardSuit.Rocket -> Color.Black
}

private val TheCrew.CardSuit.textColor get() = when(this) {
    TheCrew.CardSuit.Yellow -> Color.Black
    TheCrew.CardSuit.Pink -> Color.Black
    TheCrew.CardSuit.Green -> Color.Black
    TheCrew.CardSuit.Blue -> Color.Black
    TheCrew.CardSuit.Rocket -> Color.White
}