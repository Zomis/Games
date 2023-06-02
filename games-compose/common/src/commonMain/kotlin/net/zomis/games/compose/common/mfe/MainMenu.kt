package net.zomis.games.compose.common.mfe

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import net.zomis.games.compose.common.mfe.challenges.Challenge
import net.zomis.games.impl.minesweeper.Flags
import net.zomis.games.impl.minesweeper.specials.OpenFieldChallengeDifficulty

enum class SubMenu {
    None,
    Singleplayer,
    Multiplayer,
    Challenge,
}

@Composable
fun MenuScreen(component: MenuComponent) {
    var submenuView: SubMenu by remember { mutableStateOf(SubMenu.None) }
    Box(modifier = Modifier.fillMaxSize().background(color = Color(0, 0, 0x33)), contentAlignment = Alignment.Center) {
        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceBetween) {
            Box(modifier = Modifier.fillMaxHeight().weight(1f), contentAlignment = Alignment.Center) {
                MainMenu {
                    submenuView = if (submenuView == it) SubMenu.None else it
                }
            }
            AnimatedVisibility(submenuView != SubMenu.None) {
                Box(modifier = Modifier.fillMaxHeight().weight(1f).animateContentSize(), contentAlignment = Alignment.Center) {
                    Column {
                        // TODO: Add padding to the left menu, but only when content is actually shown
                        when (submenuView) {
                            SubMenu.None -> {}
                            SubMenu.Singleplayer -> SingleplayerMenu(component)
                            SubMenu.Multiplayer -> MultiplayerMenu(component)
                            SubMenu.Challenge -> DailyChallengeMenu(component)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AIButton(component: MenuComponent, ai: Flags.AI) {
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
    Button(onClick = { component.navigator.navigateTo(
        Configuration.ChallengeConfig(Challenge.OpenFieldChallenge(OpenFieldChallengeDifficulty.HARD))
    ) }) {
        Text("Open Field Challenge (Hard)")
    }
    /*
- repeewseniM: Minesweeper backwards. Click fields to hide them. Make all mines still be 100%
- Chess-sweeper: Chess + MFE --> 8x8 grid, move pieces to open a field. If you reveal a mine, your piece explodes.
- Tetri-sweeper: Get a block with numbers/unclicked fields, place it like Tetris. Create 100% mines to reduce numbers. Create line of open field to remove it. Make blocked fields on logic errors.
- Don't reveal any mines
- Start with some fields already revealed
- Click on X mines in Y moves. Start with some fields already revealed and automatically reveal more when all mines around a number are taken.
- Click on X non-mine squares without clicking on Y mines. Start with some fields revealed.
- Mark X 100% mines. Start with some fields revealed.
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
fun MainMenu(openMenu: (SubMenu) -> Unit) {
    val buttonSize = Modifier.padding(6.dp).fillMaxWidth().height(36.dp)

    Column(modifier = Modifier.width(IntrinsicSize.Max)) {
        // Green-ish continue button if a game is not finished
        // Quickstart button? Start same as last time?
        Button(modifier = buttonSize, onClick = { openMenu(SubMenu.Singleplayer) }) {
            Text("Single player")
        }
        Button(modifier = buttonSize, onClick = { openMenu(SubMenu.Multiplayer) }) {
            Text("Multiplayer")
        }
        Button(modifier = buttonSize, onClick = { openMenu(SubMenu.Challenge) }) {
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
