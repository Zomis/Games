package net.zomis.games.compose.common.mfe.challenges

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
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
fun OpenFieldChallengeGameContent(view: OpenFieldChallenge.ViewModelOFC, gameClient: GameClient) {
    var extremeSelected: Set<ViewField> by remember {
        mutableStateOf(emptySet())
    }
    if (view.difficulty == OpenFieldChallengeDifficulty.EXTREME) {
        Button(onClick = {
            gameClient.postAction("click", "[${extremeSelected.joinToString("+") {
                "${it.x},${it.y}"
            }}]")
        }) {
            Text("Done")
        }
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
                Image(painterResource("images/mfe/ofc/marked.png"), contentDescription = "marked")
            }
        }
    }

}