package net.zomis.games.ecs

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import net.zomis.games.PlayerEliminations
import net.zomis.games.PlayerEliminationsWrite
import net.zomis.games.common.PlayerIndex
import net.zomis.games.dsl.Actionable
import net.zomis.games.dsl.GameConfigs
import net.zomis.games.dsl.flow.GameForkResult
import net.zomis.games.dsl.impl.*

// TODO: Trash this class? Since it's running as [GameFlowImpl] instead?
class ECSGame(private val gameConfig: GameConfigs, override val playerCount: Int): Game<ECSEntity> {
    private val root: ECSEntity = ECSSimpleEntity(null, null)

    override val eliminations: PlayerEliminationsWrite = PlayerEliminations(playerCount)
    override val actions: Actions<ECSEntity>
        get() = TODO("Not yet implemented")
    override val actionsInput: Channel<Actionable<ECSEntity, out Any>> = Channel()
    override val feedbackFlow: Channel<FlowStep> = Channel()

    private val replayState = ReplayState(StateKeeper(), eliminations, gameConfig)
    private var job: Job? = null

    override suspend fun start(coroutineScope: CoroutineScope) {
        if (this.job != null) throw IllegalStateException("Game already started")
        feedbackFlow.send(FlowStep.GameStarted(this, gameConfig))
        replayState.stateKeeper.preSetup(this) { feedbackFlow.send(it) }
//        setupContext.model.onStart(GameStartContext(gameConfig, model, replayState, playerCount))
//        setupContext.actionRulesDsl?.invoke(rules)
//        rules.gameStart()
        feedbackFlow.send(FlowStep.GameSetup(this, gameConfig, replayState.stateKeeper.lastMoveState()))

        this.job = coroutineScope.launch(CoroutineName("Actions job for $this")) {
            for (action in actionsInput) {
                replayState.stateKeeper.clear()
                val oldEliminations = eliminations.eliminations()
                replayState.stateKeeper.preMove(action) {
                    feedbackFlow.send(it)
                }
                val result = actions.type(action.actionType)?.perform(action.playerIndex, action.parameter)
                if (result != null) {
                    feedbackFlow.send(result as FlowStep)
                    eliminations.eliminations().minus(oldEliminations.toSet()).forEach {
                        feedbackFlow.send(FlowStep.Elimination(it))
                    }
                    awaitInput()
                }
                if (isGameOver()) {
                    break
                }
            }
        }
        awaitInput()
    }

    private suspend fun awaitInput() {
        replayState.stateKeeper.logs().toList().forEach {
            feedbackFlow.send(FlowStep.Log(it))
        }
        replayState.stateKeeper.clearLogs()
        feedbackFlow.send(if (this.isGameOver()) FlowStep.GameEnd else FlowStep.AwaitInput)
    }

    override fun stop() {
        this.actionsInput.close()
        this.feedbackFlow.close()
        this.job?.cancel()
    }

    override val model: ECSEntity get() = root
    override val gameType: String
        get() = TODO("Not yet implemented")

    override suspend fun copy(): GameForkResult<ECSEntity> {
        TODO("Not yet implemented")
    }

    override fun view(playerIndex: PlayerIndex): Map<String, Any?> {
        TODO("Not yet implemented")
    }

}
