package net.zomis.games.compose.common.game

import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.zomis.games.PlayerEliminations
import net.zomis.games.PlayerEliminationsRead
import net.zomis.games.compose.common.gametype.GameTypeDetails
import net.zomis.games.compose.common.gametype.SupportedGames
import net.zomis.games.dsl.GameListener
import net.zomis.games.dsl.impl.FlowStep
import net.zomis.games.dsl.impl.Game
import net.zomis.games.dsl.impl.LogEntry

class LocalGameComponent(
    coroutineScope: CoroutineScope,
    override val gameTypeDetails: GameTypeDetails,
    playerCount: Int,
    playerIndex: Int,
): GameComponent {
    override val gameClient: GameClient = LocalGameClient(
        coroutineScope, playerIndex, playerCount, gameTypeDetails
    )
    override val viewDetails: SupportedGames.GameViewDetails
        get() = SupportedGames.GameViewDetailsImpl(gameClient.view, gameClient)
}

class LocalGameClient(
    private val coroutineScope: CoroutineScope,
    override val playerIndex: Int,
    playerCount: Int,
    gameTypeDetails: GameTypeDetails,
) : GameClient, GameListener {
    private var game: Game<Any>? = null

    override val gameType: String = gameTypeDetails.gameType
    override val eliminations: MutableValue<PlayerEliminationsRead> = MutableValue(PlayerEliminations(playerCount))
    override val view: MutableValue<Any> = MutableValue(Unit)
    override val logs: MutableValue<List<LogEntry>> = MutableValue(emptyList())
    private val initLock = Mutex(locked = true)

    init {
        coroutineScope.launch {
            game = gameTypeDetails.gameEntryPoint.setup().startGame(this, playerCount) {
                listOf(this@LocalGameClient)
            }
            eliminations.value = game!!.eliminations
            view.value = game!!.view(playerIndex)
            initLock.unlock() // This coroutine will not finish because the game keeps running, therefore use a lock.
        }
    }

    override suspend fun performAction(actionType: String, serializedParameter: Any) {
        initLock.withLock {  }
        val game = game
        check(game != null)
        val actionTypeEntry = game.actions.type(actionType) ?: return
        val action = actionTypeEntry.createActionFromSerialized(playerIndex, serializedParameter)
        game.actionsInput.send(action)
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
            is FlowStep.AwaitInput -> {
                view.value = game.view(playerIndex)
            }
            is FlowStep.GameEnd -> {

            }
            is FlowStep.Log -> {
                val entry = step.log.forPlayer(playerIndex)
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