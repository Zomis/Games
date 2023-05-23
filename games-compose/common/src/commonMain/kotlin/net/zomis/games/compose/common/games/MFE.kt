package net.zomis.games.compose.common.games

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import net.zomis.games.components.Point
import net.zomis.games.compose.common.PlayerProfile
import net.zomis.games.compose.common.game.GameClient
import net.zomis.games.compose.common.games.viewtest.ViewModel2
import net.zomis.games.server2.invites.PlayerInfo

@Composable
private fun ColumnScope.PlayerBox(background: Color, players: List<PlayerInfo>, playerScores: (Int) -> String, listIndex: Int) {
    Box(modifier = Modifier.weight(0.4f).padding(12.dp).background(background), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth(0.5f), horizontalAlignment = Alignment.CenterHorizontally) {
            PlayerProfile(players[listIndex], modifier = Modifier)
            Text(playerScores.invoke(listIndex))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MFE(view: ViewModel2, gameClient: GameClient) {
    println("Recompose MFE: $view")
    val players = gameClient.players.subscribeAsState().value
    Row {
        Column(Modifier.weight(0.3f), horizontalAlignment = Alignment.CenterHorizontally) {
            PlayerBox(Color.Cyan, players, { view.players[it].score.toString() }, 0)
            Text(view.minesRemaining.toString())
            PlayerBox(Color.Red, players, { view.players[it].score.toString() }, 1)
        }
        GridViewAutoScale(
            view.grid.rect.width(), view.grid.rect.height(), view.grid.grid,
            modifier = Modifier.padding(12.dp).weight(0.7f).fillMaxHeight(),
        ) { x, y, field, tileModifier ->
            var highlight by remember {
                mutableStateOf(false)
            }

            Box(tileModifier.clickable(enabled = true) {
                gameClient.postAction("use", "default@${x},${y}")
            }.onClick(matcher = PointerMatcher.mouse(PointerButton.Secondary), onClick = {
                highlight = !highlight
            }).alpha(if (highlight) 0.3f else 1.0f)) {
                println("Compose $x $y")
                val f = field!!
                when {
                    !f.clicked -> Image(
                        painterResource("images/mfe/classic_unknown.png"),
                        contentDescription = "unknown"
                    )

                    f.knownMineValue != null && f.knownMineValue!! > 0 -> {
                        val i = "m" + (field.playedBy ?: "_null")
                        Image(painterResource("images/mfe/classic_$i.png"), contentDescription = i)
                    }

                    else -> {
                        val i = f.knownValue
                        Image(painterResource("images/mfe/classic_$i.png"), contentDescription = i.toString())
                    }
                }
            }
        }

    }

}
