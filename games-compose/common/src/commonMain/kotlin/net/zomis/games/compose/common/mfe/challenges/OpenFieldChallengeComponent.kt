package net.zomis.games.compose.common.mfe.challenges

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import net.zomis.games.compose.common.Navigator
import net.zomis.games.compose.common.game.GameClient
import net.zomis.games.compose.common.game.GameComponent
import net.zomis.games.compose.common.game.GameContent
import net.zomis.games.compose.common.games.MFEGridView
import net.zomis.games.impl.minesweeper.ViewField
import net.zomis.games.impl.minesweeper.specials.OpenFieldChallenge
import net.zomis.games.impl.minesweeper.specials.OpenFieldChallengeDifficulty

interface ChallengeComponent
interface OpenFieldChallengeComponent : ChallengeComponent {
    val gameComponent: GameComponent
    fun back()
}

class DefaultOpenFieldChallengeComponent(
    override val gameComponent: GameComponent,
    private val navigator: Navigator<*>,
) : OpenFieldChallengeComponent {
    override fun back() = navigator.pop()

}

@Composable
fun OpenFieldChallengeScreen(component: OpenFieldChallengeComponent) {
    Column(modifier = Modifier.fillMaxSize()) {
        Button(onClick = { component.back() }) {
            Text("Back")
        }
        GameContent(component.gameComponent)
    }
}

@Composable
fun RowScope.OpenFieldChallengeStats(scores: OpenFieldChallenge.OFCScore, difficulty: OpenFieldChallengeDifficulty, extremeConfirm: () -> Unit) {
    Column(modifier = Modifier.padding(6.dp)) {
        Text("Points")
        Text(scores.points.toString())
        Spacer(modifier = Modifier.height(4.dp))

        Text("Cleared boards")
        Text(scores.clearedBoards.toString())
        Spacer(modifier = Modifier.height(4.dp))

        Text("Mines required")
        Text(scores.minesRequired.toString())
        Spacer(modifier = Modifier.height(4.dp))

        Text("Mines found")
        Text(scores.minesFound.toString())
        Spacer(modifier = Modifier.height(4.dp))

        Text("Mistakes allowed")
        Text(scores.mistakesAllowed.toString())

        if (difficulty == OpenFieldChallengeDifficulty.EXTREME) {
            Spacer(modifier = Modifier.height(4.dp))
            Button(onClick = extremeConfirm) {
                Text("Done")
            }
        }
    }
}

@Composable
fun OpenFieldChallengeGameContent(view: OpenFieldChallenge.ViewModelOFC, gameClient: GameClient) {
    var extremeSelected: Set<ViewField> by remember {
        mutableStateOf(emptySet())
    }
    Row {
        OpenFieldChallengeStats(view.scores, view.difficulty) {
            gameClient.postAction(
                "confirm", extremeSelected.joinToString("+") {
                    "${it.x},${it.y}"
                }
            )
            extremeSelected = emptySet()
        }
        MFEGridView(modifier = Modifier.fillMaxSize(), view.model.grid) { x, y, field, tileModifier ->
            Box(tileModifier.clickable {
                if (view.difficulty == OpenFieldChallengeDifficulty.EXTREME) {
                    extremeSelected = if (field in extremeSelected) extremeSelected - field else extremeSelected + field
                } else {
                    gameClient.postAction("click", "${x},${y}")
                }
            }) {
                when {
                    !field.clicked -> Image(
                        painterResource("images/mfe/classic_unknown.png"),
                        contentDescription = "unknown"
                    )
                    field.knownMineValue != null && field.knownMineValue!! > 0 -> {
                        val i = "m" + (field.playedBy ?: "_null")
                        Image(painterResource("images/mfe/classic_$i.png"), contentDescription = i)
                    }

                    else -> {
                        val i = field.knownValue
                        Image(painterResource("images/mfe/classic_$i.png"), contentDescription = i.toString())
                    }
                }
                if (field.point() in view.correctAnswers) {
                    Image(painterResource("images/mfe/classic_m_null.png"), contentDescription = "marked")
                }
                if (field.point() in view.mistakesMade) {
                    Image(painterResource("images/mfe/ofc/bad.png"), contentDescription = "marked")
                }
                if (field in extremeSelected) {
                    Image(
                        painter = painterResource("images/mfe/ofc/marked.png"),
                        contentDescription = "marked",
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}