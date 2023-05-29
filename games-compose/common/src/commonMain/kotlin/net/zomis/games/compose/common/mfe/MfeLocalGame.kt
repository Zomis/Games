package net.zomis.games.compose.common.mfe

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import net.zomis.games.compose.common.Navigator
import net.zomis.games.compose.common.game.GameContent
import net.zomis.games.compose.common.game.LocalGameComponent
import net.zomis.games.compose.common.gametype.GameTypeDetails
import net.zomis.games.dsl.GameListener
import net.zomis.games.dsl.impl.FlowStep
import net.zomis.games.impl.minesweeper.Flags
import net.zomis.games.listeners.LimitedNextViews
import net.zomis.games.listeners.NoOpListener

interface MfeLocalGameComponent {
    val gameType: GameTypeDetails
    val ai: Flags.AI?
    fun back()
}

class DefaultMfeLocalGameComponent(
    context: ComponentContext,
    private val navigator: Navigator<*>,
    override val ai: Flags.AI?,
    override val gameType: GameTypeDetails,
) : MfeLocalGameComponent {
    override fun back() = navigator.pop()
}


@Composable
fun LocalGameContent(component: MfeLocalGameComponent) {
    val coroutineScope = rememberCoroutineScope()
    val gameTypeDetails = component.gameType
    val ai = component.ai

    val playerCount = remember { gameTypeDetails.gameEntryPoint.setup().playersCount.random() }
    val playerIndex = MutableValue(0)
    fun createGameComponent(): LocalGameComponent {
        return LocalGameComponent(coroutineScope, gameTypeDetails, playerCount, playerIndex) {
            val hotSeatListener = if (ai == null) {
                GameListener { _, step ->
                    if (step is FlowStep.AwaitInput) {
                        playerIndex.value = (it.model as Flags.Model).currentPlayer
                    }
                }
            } else NoOpListener

            listOf(
                hotSeatListener,
                LimitedNextViews(10),
                if (ai != null) gameTypeDetails.gameEntryPoint.setup().findAI(ai.publicName)!!.gameListener(it, 1)
                else NoOpListener
            )
        }
    }

    var gamePlayComponent by remember { mutableStateOf(createGameComponent()) }
    Column(Modifier.fillMaxSize()) {
        Row {
            Button(onClick = { component.back() }, modifier = Modifier.padding(end = 4.dp)) {
                Text("Back")
            }
            Button(onClick = { gamePlayComponent = createGameComponent() }) {
                Text("New Game")
            }

        }
        GameContent(gamePlayComponent)
    }

}