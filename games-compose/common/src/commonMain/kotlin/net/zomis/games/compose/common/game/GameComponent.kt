package net.zomis.games.compose.common.game

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.*
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
import net.zomis.games.compose.common.TestPlatform
import net.zomis.games.compose.common.gametype.GameTypeDetails
import net.zomis.games.compose.common.gametype.SupportedGames
import net.zomis.games.compose.common.network.ClientConnection
import net.zomis.games.compose.common.network.ClientToServerMessage
import net.zomis.games.compose.common.network.Message
import net.zomis.games.dsl.impl.LogEntry

interface GameComponent {
    val gameTypeDetails: GameTypeDetails
    val gameClient: GameClient
    val viewDetails: SupportedGames.GameViewDetails
}

class DefaultGameComponent(
    componentContext: ComponentContext,
    private val connection: ClientConnection,
    gameStarted: Message.GameStarted,
    override val gameTypeDetails: GameTypeDetails
) : GameComponent {
    private val scope = CoroutineScope(Dispatchers.Default, componentContext.lifecycle)
    val playerIndex: Int? = gameStarted.indexAccess().keys.firstOrNull()
    private val _eliminations = MutableValue(PlayerEliminations(gameStarted.players.size))
    private val _view = MutableValue<Any>(Unit)
    private val _logs = MutableValue(emptyList<LogEntry>())
    override val gameClient: GameClient = NetworkGameClient(
        gameStarted, connection, scope, MutableValue(playerIndex ?: -1),
        _eliminations, _view, _logs
    )
    override val viewDetails: SupportedGames.GameViewDetails
        get() = SupportedGames.GameViewDetailsImpl(_view, gameClient)
    val gameId = gameStarted.gameId

    init {
        componentContext.lifecycle.doOnCreate {
            scope.launch {
                connection.messages.onSubscription {
                    connection.send(ClientToServerMessage.GameJoin(gameStarted.gameType, gameId))
                }.filterIsInstance<Message.GameMessage>().filter { it.gameId == gameId }.collect {
                    when (it) {
                        is Message.GameMessage.GameView -> {
                            _view.value = it.view
                        }
                        is Message.GameMessage.UpdateView -> {
                            updateView()
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
                updateView()
            }
        }
    }

    private suspend fun updateView() {
        connection.send(
            ClientToServerMessage.GameView(gameTypeDetails.gameType, gameId, playerIndex ?: -1, null, null)
        )
    }


}

@Composable
fun GameContent(component: GameComponent) {
    val view = component.gameClient.view.subscribeAsState()
    if (view.value is Unit) return
    val value = view.value
    if (value is Map<*, *> && value.isEmpty()) return

    Row(modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxHeight().weight(0.7f).background(Color.DarkGray)) {
            component.gameTypeDetails.component.invoke(component.viewDetails)
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
    val gameStore = SupportedGames(TestPlatform())
    val gameType = "NoThanks"
    val gameTypeDetails = gameStore.getGameType(gameType)
    if (gameTypeDetails == null) {
        Text("Game $gameType not found")
        return
    }
    val playerCount = remember { gameTypeDetails.gameEntryPoint.setup().playersCount.random() }
    val playerIndex = MutableValue(0)
    val component = LocalGameComponent(coroutineScope, gameTypeDetails, playerCount, playerIndex)
    playerIndex.subscribeAsState()

    LaunchedEffect(Unit) {
        component.gameClient.performAction("play", Point(0, 2))
    }
    Column(Modifier.fillMaxSize()) {
        Row {
            for (i in component.gameClient.playerIndices) {
                Button(onClick = { playerIndex.value = i }) {
                    Text(i.toString())
                }
            }
        }
        GameContent(component)
    }

}