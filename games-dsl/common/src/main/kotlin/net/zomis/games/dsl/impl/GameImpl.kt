package net.zomis.games.dsl.impl

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import net.zomis.games.PlayerElimination
import net.zomis.games.PlayerEliminations
import net.zomis.games.PlayerEliminationsWrite
import net.zomis.games.common.GameEvents
import net.zomis.games.common.PlayerIndex
import net.zomis.games.dsl.*

class GameControllerContext<T : Any>(
    override val game: Game<T>, override val playerIndex: Int
): GameControllerScope<T> {
    override val model: T get() = game.model
    fun view(): Map<String, Any?> = game.view(playerIndex)
}
interface GameControllerScope<T : Any> {
    val game: Game<T>
    val model: T
    val playerIndex: Int
}
typealias GameController<T> = (GameControllerScope<T>) -> Actionable<T, Any>?

class GameSetupImpl<T : Any>(gameSpec: GameSpec<T>) {

    val gameType: String = gameSpec.name
    internal val context = GameDslContext<T>()
    init {
        gameSpec(context)
        context.modelDsl(context.model)
    }

    val playersCount: IntRange = context.model.playerCount

    fun createGameWithOldConfig(playerCount: Int, config: Any): Game<T> {
        return createGame(playerCount, configs().also { it.set("", config) })
    }

    fun createGameWithDefaultConfig(playerCount: Int): Game<T> = createGame(playerCount, configs())
    fun createGame(playerCount: Int) = createGameWithState(playerCount, configs(), StateKeeper())

    fun createGame(playerCount: Int, config: GameConfigs): Game<T>
        = this.createGameWithState(playerCount, config, StateKeeper())

    fun createGameWithState(playerCount: Int, config: GameConfigs, stateKeeper: StateKeeper): Game<T> {
        if (playerCount !in playersCount) {
            throw IllegalArgumentException("Invalid number of players: $playerCount, expected $playersCount")
        }
        return context.createGame(playerCount, config, stateKeeper)
    }

    fun configs(): GameConfigs = context.configs()

}

@DslMarker
annotation class GameMarker

sealed class FlowStep {
    object GameEnd: FlowStep()
    data class Elimination(val elimination: PlayerElimination): FlowStep()
    data class ActionPerformed<T: Any>(
        val action: Actionable<T, Any>,
        val actionImpl: ActionTypeImplEntry<T, Any>,
        val replayState: Map<String,  Any>
    ): FlowStep() {
        val playerIndex: Int get() = action.playerIndex
        val parameter: Any get() = action.parameter
    }
    data class IllegalAction(val actionType: String, val playerIndex: Int, val parameter: Any): FlowStep()
    data class Log(val log: ActionLogEntry): FlowStep()
    data class RuleExecution(val ruleName: String, val values: Any): FlowStep()
    data class GameSetup(val playerCount: Int, val config: GameConfigs, val state: Map<String, Any>): FlowStep()
    object AwaitInput: FlowStep()
    object NextView : FlowStep()
}

interface Game<T: Any> {
    val playerCount: Int
    val playerIndices: IntRange get() = 0 until playerCount
    val config: Any
    val eliminations: PlayerEliminationsWrite
    val model: T
    val stateKeeper: StateKeeper
    val actions: Actions<T>
    val actionsInput: Channel<Actionable<T, out Any>>
    val feedbackFlow: MutableSharedFlow<FlowStep>
    suspend fun start(coroutineScope: CoroutineScope)
    fun copy(copier: (source: T, destination: T) -> Unit): Game<T>
    fun isGameOver(): Boolean
    fun isRunning() = !isGameOver()
    fun view(playerIndex: PlayerIndex): Map<String, Any?>
    fun viewRequest(playerIndex: PlayerIndex, key: String, params: Map<String, Any>): Any?
}

class GameImpl<T : Any>(
    private val setupContext: GameDslContext<T>,
    override val playerCount: Int,
    val gameConfig: GameConfigs,
    override val stateKeeper: StateKeeper
): Game<T>, GameFactoryScope<Any>, GameEventsExecutor {

    override val config: Any get() = gameConfig.oldStyleValue()
    override val eliminationCallback = PlayerEliminations(playerCount)
    override val eliminations: PlayerEliminationsWrite get() = eliminationCallback
    override val model = setupContext.model.factory(this)
    private val replayState = ReplayState(stateKeeper, eliminationCallback, gameConfig)
    private val rules = GameActionRulesContext(gameConfig, model, replayState, eliminationCallback)

    override suspend fun start(coroutineScope: CoroutineScope) {
        setupContext.model.onStart(GameStartContext(gameConfig, model, replayState, playerCount))
        setupContext.actionRulesDsl?.invoke(rules)
        rules.gameStart()
        feedbackFlow.emit(FlowStep.GameSetup(playerCount, gameConfig, stateKeeper.lastMoveState()))

        coroutineScope.launch {
            for (action in actionsInput) {
                println("GameImpl received action $action")
                actions.type(action.actionType)?.perform(action.playerIndex, action.parameter)
                awaitInput()
                if (isGameOver()) break
            }
        }
        awaitInput()
    }

    private suspend fun awaitInput() {
        feedbackFlow.emit(if (this.isGameOver()) FlowStep.GameEnd else FlowStep.AwaitInput)
    }

    override val actions = ActionsImpl(model, rules, replayState)

    override val actionsInput: Channel<Actionable<T, out Any>> = Channel()
    override val feedbackFlow: MutableSharedFlow<FlowStep> = MutableSharedFlow()

    override fun copy(copier: (source: T, destination: T) -> Unit): GameImpl<T> {
        val copy = GameImpl(setupContext, playerCount, gameConfig, stateKeeper)
        copier(this.model, copy.model)
        this.eliminations.eliminations().forEach { copy.eliminations.eliminate(it) }
        return copy
    }

    override fun view(playerIndex: PlayerIndex): Map<String, Any?> {
        val view = GameViewContext(this, playerIndex)
        if (model is Viewable) {
            val map = model.toView(playerIndex) as Map<String, Any?>
            map.forEach { entry -> view.value(entry.key) { entry.value } }
        }
        setupContext.viewDsl?.invoke(view)
        rules.view(view)
        return view.result()
    }

    override fun isGameOver(): Boolean = eliminationCallback.isGameOver()

    override fun viewRequest(playerIndex: PlayerIndex, key: String, params: Map<String, Any>): Any? {
        val view = GameViewContext(this, playerIndex)
        setupContext.viewDsl?.invoke(view)
        return view.request(playerIndex, key, params)
    }

    override val events: GameEventsExecutor get() = this
    override fun <E> fire(executor: GameEvents<E>, event: E) = this.rules.fire(executor, event)
    override fun <E: Any> config(config: GameConfig<E>): E = gameConfig.get(config)
}
