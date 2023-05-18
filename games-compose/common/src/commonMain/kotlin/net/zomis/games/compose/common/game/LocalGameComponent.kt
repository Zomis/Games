package net.zomis.games.compose.common.game

import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.zomis.games.PlayerEliminations
import net.zomis.games.PlayerEliminationsRead
import net.zomis.games.compose.common.gametype.GameTypeDetails
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
}

class LocalGameClient(
    coroutineScope: CoroutineScope,
    override val playerIndex: Int,
    playerCount: Int,
    gameTypeDetails: GameTypeDetails,
) : GameClient, GameListener {
    private var game: Game<Any>? = null

    override val gameType: String = gameTypeDetails.gameType
    override val eliminations: MutableValue<PlayerEliminationsRead> = MutableValue(PlayerEliminations(playerCount))
    override val view: MutableValue<Any> = MutableValue(Unit)
    override val logs: MutableValue<List<LogEntry>> = MutableValue(emptyList())

    init {
        coroutineScope.launch {
            game = gameTypeDetails.gameEntryPoint.setup().startGame(this, playerCount) {
                listOf(this@LocalGameClient)
            }
            eliminations.value = game!!.eliminations
            view.value = game!!.view(playerIndex)
        }
    }

    override suspend fun performAction(actionTypeKey: String, serializedParameter: Any) {
        val game = game
        check(game != null)
        val actionType = game.actions.type(actionTypeKey)
        check(actionType != null)
        val action = actionType.createActionFromSerialized(playerIndex, serializedParameter)
        game.actionsInput.send(action)
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