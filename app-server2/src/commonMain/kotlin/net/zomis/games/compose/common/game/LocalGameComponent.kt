package net.zomis.games.compose.common.game

import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.operator.map
import com.arkivanov.decompose.value.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.zomis.games.PlayerEliminations
import net.zomis.games.PlayerEliminationsRead
import net.zomis.games.compose.common.TestData
import net.zomis.games.compose.common.gametype.GameTypeDetails
import net.zomis.games.compose.common.gametype.SupportedGames
import net.zomis.games.dsl.GameConfigs
import net.zomis.games.dsl.GameListener
import net.zomis.games.dsl.impl.FlowStep
import net.zomis.games.dsl.impl.Game
import net.zomis.games.dsl.impl.LogEntry
import net.zomis.games.server2.invites.PlayerInfo

class LocalGameComponent(
    coroutineScope: CoroutineScope,
    override val gameTypeDetails: GameTypeDetails,
    playerCount: Int,
    playerIndex: Value<Int>,
    config: (GameConfigs) -> GameConfigs = { it },
    listeners: (Game<Any>) -> List<GameListener> = { emptyList() },
): GameComponent {
    override val gameClient: GameClient = LocalGameClient(
        coroutineScope, playerIndex, playerCount, gameTypeDetails, listeners, config
    )
    override val viewDetails: SupportedGames.GameViewDetails
        get() = SupportedGames.GameViewDetailsImpl(gameClient.view, gameClient)
}

class LocalGameClient(
    private val coroutineScope: CoroutineScope,
    override val playerIndex: Value<Int>,
    override val playerCount: Int,
    gameTypeDetails: GameTypeDetails,
    listeners: (Game<Any>) -> List<GameListener>,
    configInit: (GameConfigs) -> GameConfigs = { it },
) : GameClient, GameListener {
    private var game: Game<Any>? = null

    override val gameType: String = gameTypeDetails.gameType
    override val eliminations: MutableValue<PlayerEliminationsRead> = MutableValue(PlayerEliminations(playerCount))
    override val view: MutableValue<Any> = MutableValue(Unit)
    override val logs: MutableValue<List<LogEntry>> = MutableValue(emptyList())
    override val players: Value<List<PlayerInfo>> = MutableValue((0 until playerCount).map(TestData::playerInfo))
    private val initLock = Mutex(locked = true)

    init {
        coroutineScope.launch {
            val setup = gameTypeDetails.gameEntryPoint.setup()
            val gameConfig = setup.configs().let(configInit)
            game = setup.startGameWithConfig(this, playerCount, config = gameConfig) {
                listOf(this@LocalGameClient) + listeners.invoke(it)
            }
            eliminations.value = game!!.eliminations
            view.value = game!!.view(playerIndex.value)
            playerIndex.subscribe {
                view.value = game!!.view(it)
            }
            initLock.unlock() // This coroutine will not finish because the game keeps running, therefore use a lock.
        }
    }

    override suspend fun performAction(actionType: String, serializedParameter: Any) {
        initLock.withLock {  }
        val game = game
        check(game != null)
        val actionTypeEntry = game.actions.type(actionType) ?: return
        try {
            val action = actionTypeEntry.createActionFromSerialized(playerIndex.value, serializedParameter)
            game.actionsInput.send(action)
        } catch (e: Exception) {
            println(e)
        }
    }

    override fun postAction(actionType: String, serializedParameter: Any) {
        coroutineScope.launch {
            performAction(actionType, serializedParameter)
        }
    }

    override suspend fun handle(coroutineScope: CoroutineScope, step: FlowStep) {
        println("Handle: $step")
        val game = this.game ?: return
        when (step) {
            is FlowStep.AwaitInput, is FlowStep.GameEnd -> {
                view.value = game.view(playerIndex.value)
            }
            is FlowStep.Log -> {
                val entry = step.log.forPlayer(playerIndex.value)
                if (entry != null) {
                    logs.update { it + entry }
                }
            }
            is FlowStep.Elimination -> {
                eliminations.value = game.eliminations
            }

            else -> {}
        }
    }

}