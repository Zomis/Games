package net.zomis.games.compose.common.mfe

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun MenuScreen(component: MenuComponent) {
    val submenuView: MutableState<@Composable () -> Unit> = remember { mutableStateOf({}) }
    Box(modifier = Modifier.fillMaxSize().background(color = Color(0, 0, 0x33)), contentAlignment = Alignment.Center) {
        Row {
            MainMenu { submenuView.value = it }
            submenuView.value.invoke()
        }
    }
}

@Composable
fun AIButton(ai: String) {
    Button(onClick = {}) {
        Text(ai)
    }
}

@Composable
fun SingleplayerMenu() {
    Column {
        AIButton("Loser")
        AIButton("Complete Idiot")
    }
}

@Composable
fun MultiplayerMenu() {
    // Hotseat, steam, browse public, connect to server...
}

@Composable
fun DailyChallengeMenu() {
    /*
- Lose against AI Loser
- With more weapon-combinations
- Chess Neighbors
- FloodFill?
- Double Line Neighbors
- 4-dir neighbors
- Treasure Hunt win game
- Treasure Hunt get mines
- Open Field Challenge Easy/Medium/Hard/Extreme
- Training Session - Probability finder
- Tentaizu (all 100% mines)
- Special maps (blocked fields etc.)
- What Would AI Do
- Mirror maps? Or click somewhere, reveal something else? Interconnected fields?
    */
}

@Composable
@Preview
fun MainMenu(openMenu: (@Composable () -> Unit) -> Unit) {
    val buttonSize = Modifier.padding(6.dp).fillMaxWidth().height(36.dp)
//    MainMenuScreen()
//    SingleplayerMenu()
//    return

    Column(modifier = Modifier.width(IntrinsicSize.Max)) {
        // Green-ish continue button if a game is not finished
//        Button(modifier = buttonSize, onClick = {}) {
//            Text("Quickstart?")
//        }
        Button(modifier = buttonSize, onClick = { openMenu { SingleplayerMenu() } }) {
            Text("Single player")
        }
        Button(modifier = buttonSize, onClick = { openMenu { MultiplayerMenu() } }) {
            Text("Multiplayer")
        }
        Button(modifier = buttonSize, onClick = { openMenu { DailyChallengeMenu() } }) {
            Text("Daily challenge") // Glide menu to the left, show details / more options to the right?
        }
        Button(modifier = buttonSize, onClick = {}) {
            Text("Settings")
        }
        Button(modifier = buttonSize, onClick = {}) {
            Text("About")
        }
    }
}
