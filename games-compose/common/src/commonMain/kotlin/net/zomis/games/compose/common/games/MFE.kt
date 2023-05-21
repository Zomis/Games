package net.zomis.games.compose.common.games

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import net.zomis.games.compose.common.PlayerProfile
import net.zomis.games.compose.common.game.GameClient
import net.zomis.games.impl.minesweeper.ViewModel
import net.zomis.games.server2.invites.PlayerInfo

@Composable
private fun ColumnScope.PlayerBox(background: Color, players: List<PlayerInfo>, viewModel: ViewModel, listIndex: Int) {
    Box(modifier = Modifier.weight(0.4f).padding(12.dp).background(background), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth(0.5f), horizontalAlignment = Alignment.CenterHorizontally) {
            PlayerProfile(players[listIndex], modifier = Modifier)
            Text(viewModel.players[listIndex].score.toString())
        }
    }
}

@Composable
fun MFE(view: ViewModel, gameClient: GameClient) {
    val players = gameClient.players.subscribeAsState().value
    Row {
        Column(Modifier.weight(0.3f), horizontalAlignment = Alignment.CenterHorizontally) {
            PlayerBox(Color.Cyan, players, view, 0)
            Text(view.minesRemaining.toString())
            PlayerBox(Color.Red, players, view, 1)
        }
        GridView(
            view.grid.rect.width(), view.grid.rect.height(), view.grid.grid,
            modifier = Modifier, tileSize = 32.dp, tilePadding = 0.dp,
            onClick = { point, viewField ->
                gameClient.postAction("use", "default@${point.x},${point.y}")
            },
            clickable = { x, y, field -> true }
        ) { x, y, field ->
            val f = field!!
            if (!f.clicked) {
                Image(painterResource("images/mfe/classic_unknown.png"), contentDescription = "unknown")
                return@GridView
            }
            if (f.knownMineValue != null && f.knownMineValue!! > 0) {
                val i = "m" + (field.playedBy ?: "_null")
                Image(painterResource("images/mfe/classic_$i.png"), contentDescription = i)
            } else {
                val i = f.knownValue
                Image(painterResource("images/mfe/classic_$i.png"), contentDescription = i.toString())
            }
        }

    }

}
