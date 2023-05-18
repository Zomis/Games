package net.zomis.games.compose.common.game

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.update
import com.arkivanov.essenty.lifecycle.doOnCreate
import com.arkivanov.essenty.lifecycle.doOnResume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import net.zomis.games.PlayerElimination
import net.zomis.games.PlayerEliminations
import net.zomis.games.components.Point
import net.zomis.games.compose.common.CoroutineScope
import net.zomis.games.compose.common.gametype.GameTypeDetails
import net.zomis.games.compose.common.gametype.SupportedGames
import net.zomis.games.compose.common.network.ClientConnection
import net.zomis.games.compose.common.network.ClientToServerMessage
import net.zomis.games.compose.common.network.Message
import net.zomis.games.dsl.impl.LogEntry

interface GameComponent {
    val gameTypeDetails: GameTypeDetails
    val gameClient: GameClient
}

class DefaultGameComponent(
    componentContext: ComponentContext,
    connection: ClientConnection,
    gameStarted: Message.GameStarted,
    override val gameTypeDetails: GameTypeDetails
) : GameComponent {
    private val scope = CoroutineScope(Dispatchers.Default, componentContext.lifecycle)
    val playerIndex: Int? = gameStarted.indexAccess().keys.firstOrNull()
    private val _eliminations = MutableValue(PlayerEliminations(gameStarted.players.size))
    private val _view = MutableValue<Any>(Unit)
    private val _logs = MutableValue(emptyList<LogEntry>())
    override val gameClient: GameClient = NetworkGameClient(
        gameStarted, connection, scope, playerIndex ?: -1,
        _eliminations, _view, _logs
    )

    init {
        val gameId = gameStarted.gameId
        componentContext.lifecycle.doOnCreate {
            scope.launch {
                connection.messages.onSubscription {
                    connection.send(ClientToServerMessage.GameJoin(gameStarted.gameType, gameId))
                }.filterIsInstance<Message.GameMessage>().filter { it.gameId == gameId }.collect {
                    when (it) {
                        is Message.GameMessage.GameView -> {
                            _view.value = it.view
                        }
                        is Message.GameMessage.PlayerEliminated -> {
                            _eliminations.update { oldValue ->
                                oldValue + PlayerElimination(it.player, it.winResult, it.position)
                            }
                        }
                        is Message.GameMessage.ActionLog -> {
                            _logs.update { oldValue ->
                                oldValue + LogEntry(it.parts, it.private)
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
        componentContext.lifecycle.doOnResume {
            scope.launch {
                connection.send(ClientToServerMessage.GameView(gameStarted.gameType, gameId, playerIndex ?: -1, null, null))
            }
        }
    }


}

@Composable
fun GameContent(component: GameComponent) {
    val view = component.gameClient.view.subscribeAsState()

    Row(modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxHeight().weight(0.7f).background(Color.DarkGray)) {
            component.gameTypeDetails.component.invoke(view.value)
        }
        Box(Modifier.fillMaxHeight().weight(0.3f).background(Color.Gray)) {
            Text("ActionLog")
        }
    }
}

@Composable
@Preview
fun GameContentPreview() {
    val coroutineScope = rememberCoroutineScope()
    val gameStore = SupportedGames()
    val gameType = "DSL-TTT"
    val gameTypeDetails = gameStore.getGameType(gameType)
    if (gameTypeDetails == null) {
        Text("Game $gameType not found")
        return
    }
    val playerCount = remember { gameTypeDetails.gameEntryPoint.setup().playersCount.random() }
    val playerIndex = remember { (0 until playerCount).random() }
    val component = LocalGameComponent(coroutineScope, gameTypeDetails, playerCount, playerIndex)

    LaunchedEffect(Unit) {

        component.gameClient.performAction("play", Point(0, 2))
    }

    GameContent(component)
}