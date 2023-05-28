package net.zomis.games.compose.common.mfe

import androidx.compose.animation.animateContentSize
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
import net.zomis.games.impl.minesweeper.Flags

@Composable
fun MenuScreen(component: MenuComponent) {
    val submenuView: MutableState<@Composable () -> Unit> = remember { mutableStateOf({}) }
    Box(modifier = Modifier.fillMaxSize().background(color = Color(0, 0, 0x33)), contentAlignment = Alignment.Center) {
        Row {
            MainMenu(component) { submenuView.value = it }
            Column(modifier = Modifier.animateContentSize()) {
                // TODO: Add padding to the left menu, but only when content is actually shown
                submenuView.value.invoke()
            }
        }
    }
}

@Composable
fun AIButton(component: MenuComponent, ai: Flags.AI) {
    // TODO: Check if AI is available
    Button(onClick = { component.navigator.navigateTo(Configuration.LocalGame(ai)) }) {
        Text(ai.visibleName)
    }
}

@Composable
fun SingleplayerMenu(component: MenuComponent) {
    Column {
        Flags.AI.values().forEach {
            AIButton(component, it)
        }
    }
}

@Composable
fun MultiplayerMenu(component: MenuComponent) {
    Column {
        Button(onClick = { component.navigator.navigateTo(Configuration.LocalGame(ai = null)) }) {
            Text("Local hot seat")
        }
        Button(onClick = {  }, enabled = false) {
            Text("Steam")
        }
        Button(onClick = {  }, enabled = false) {
            Text("Browse public games")
        }
        Button(onClick = {  }, enabled = false) {
            Text("Connect to server")
        }
    }
}

@Composable
fun DailyChallengeMenu(component: MenuComponent) {
    Text("No challenge today 😞")
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
fun MainMenu(component: MenuComponent, openMenu: (@Composable () -> Unit) -> Unit) {
    val buttonSize = Modifier.padding(6.dp).fillMaxWidth().height(36.dp)

    Column(modifier = Modifier.width(IntrinsicSize.Max)) {
        // Green-ish continue button if a game is not finished
        // Quickstart button? Start same as last time?
        Button(modifier = buttonSize, onClick = { openMenu { SingleplayerMenu(component) } }) {
            Text("Single player")
        }
        Button(modifier = buttonSize, onClick = { openMenu { MultiplayerMenu(component) } }) {
            Text("Multiplayer")
        }
        Button(modifier = buttonSize, onClick = { openMenu { DailyChallengeMenu(component) } }) {
            Text("Daily challenge") // Glide menu to the left, show details / more options to the right?
        }
        Button(modifier = buttonSize, onClick = {}, enabled = false) {
            Text("Settings")
        }
        Button(modifier = buttonSize, onClick = {}, enabled = false) {
            Text("About")
        }
    }
}
