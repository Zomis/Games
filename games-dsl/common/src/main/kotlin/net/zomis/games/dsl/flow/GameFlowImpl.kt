package net.zomis.games.dsl.flow

import klog.KLoggers
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import net.zomis.games.PlayerEliminations
import net.zomis.games.PlayerEliminationsWrite
import net.zomis.games.common.GameEvents
import net.zomis.games.common.PlayerIndex
import net.zomis.games.dsl.*
import net.zomis.games.dsl.impl.*

class GameFlowImpl<T: Any>(
    private val setupContext: GameDslContext<T>,
    override val playerCount: Int,
    val gameConfig: GameConfigs,
    override val stateKeeper: StateKeeper
): Game<T>, GameFactoryScope<Any>, GameEventsExecutor, GameFlowRuleCallbacks<T> {

    override val config: Any get() = gameConfig.oldStyleValue()
    private val logger = KLoggers.logger(this)
    private var lastAction: FlowStep.ActionPerformed<T>? = null
    private val mainScope = MainScope()
    val views = mutableListOf<Pair<String, ViewScope<T>.() -> Any?>>()
    private val feedbackOutput = Channel<FlowStep>()
    val feedbackReceiver: ReceiveChannel<FlowStep> get() = feedbackOutput
    private val feedbacks = mutableListOf<FlowStep>()

    override val eliminations: PlayerEliminations = PlayerEliminations(playerCount).also {
        it.callback = { elimination ->
            feedbacks.add(FlowStep.Elimination(elimination))
        }
    }
    override val events: GameEventsExecutor = this
    override val eliminationCallback: PlayerEliminationsWrite = eliminations
    override val model: T = setupContext.model.factory(this)
    val replayable = ReplayState(stateKeeper, eliminations, gameConfig)
    override val actions = GameFlowActionsImpl({ feedbacks.add(it) }, model, eliminations, replayable)
    private var gameSetupSent = false
    override val feedback: (FlowStep) -> Unit = { feedbacks.add(it) }

    val actionsInput: Channel<Any> = Channel()
    val job: Job
    init {
        val game = this
        job = mainScope.launch(Dispatchers.Default) {
            logger.info("GameFlow Coroutine started")
            try {
                val dsl = setupContext.flowDsl!!
                val flowContext = GameFlowContext(this, game, "root")
                setupContext.model.onStart(GameStartContext(gameConfig, model, replayable, playerCount))
                dsl.invoke(flowContext)
                actionDone()
                sendFeedback(FlowStep.GameEnd)
                logger.info("GameFlow Coroutine MainScope done for $game")
            } catch (e: Exception) {
                KLoggers.logger(game).error(e) { "Error in Coroutine for game $game" }
            }
        }
    }

    suspend fun sendFeedbacks() {
        feedbacks.forEach {
            sendFeedback(it)
        }
        stateKeeper.logs().forEach {
            sendFeedback(FlowStep.Log(it))
        }
        stateKeeper.clearLogs()
        feedbacks.clear()
    }

    fun destroy() {
        mainScope.cancel()
    }

    override fun copy(copier: (source: T, destination: T) -> Unit): Game<T> {
        TODO("Not yet implemented")
    }

    override fun isGameOver(): Boolean = eliminations.isGameOver()

    override fun view(playerIndex: PlayerIndex): Map<String, Any?> {
        val duplicates = views.map { it.first }.groupingBy { it }.eachCount().filter { it.value > 1 }
        if (duplicates.isNotEmpty()) logger.warn {  "Multiple keys detected in view of: $duplicates" }
        val viewContext = GameViewContext(this, playerIndex)
        return this.views.associate { it.first to it.second(viewContext) }
    }

    override fun viewRequest(playerIndex: PlayerIndex, key: String, params: Map<String, Any>): Any? {
        TODO("Not yet implemented")
    }

    suspend fun nextAction(): Actionable<T, Any>? {
        if (this.lastAction != null) {
            val last = this.lastAction!!
            this.lastAction = last.copy(replayState = replayable.stateKeeper.lastMoveState())
        }
        if (isGameOver()) {
            this.actionDone()
            return null
        }
        if (anyPlayerHasAction()) {
            this.actionDone()
            while (true) {
                if (isGameOver()) {
                    return null
                }
                if (!gameSetupSent) {
                    sendFeedback(FlowStep.GameSetup(playerCount, gameConfig, replayable.stateKeeper.lastMoveState()))
                    gameSetupSent = true
                }
                replayable.stateKeeper.clear()
                sendFeedback(FlowStep.AwaitInput)
                val action = actionsInput.receive()
                logger.info("GameFlow Coroutine Action Received: $action")
                require(action is Actionable<*, *>)
                val typeEntry = actions.type(action.actionType)
                if (typeEntry == null) {
                    sendFeedback(FlowStep.IllegalAction(action.actionType, action.playerIndex, action.parameter))
                    continue
                }
                actions.clearAndPerform(action as Actionable<T, Any>) { this.clear() }
                this.lastAction = FlowStep.ActionPerformed(action, typeEntry, replayable.stateKeeper.lastMoveState())
                runRules(GameFlowRulesState.AFTER_ACTIONS)
                return action
            }
        } else {
            logger.info("GameFlow Coroutine No Available Actions")
            sendFeedback(FlowStep.NextView)
            clear()
            runRules(GameFlowRulesState.AFTER_ACTIONS)
            return null
        }
    }

    private suspend fun actionDone() {
        runRules(GameFlowRulesState.BEFORE_RETURN)
        sendFeedbacks()
        lastAction?.also { sendFeedback(it) }
    }

    private fun anyPlayerHasAction(): Boolean {
        // Check if any player can do any action, anywhere
        return eliminations.playerIndices.any { playerIndex ->
            actions.types().any { action -> action.availableActions(playerIndex, null).any() }
        }
    }

    suspend fun sendFeedback(feedback: FlowStep) {
        logger.info("GameFlow Coroutine sends feedback: $feedback")
        this.feedbackOutput.send(feedback)
        logger.info("GameFlow Coroutine feedback sent: $feedback, continuing coroutine...")
    }

    private fun runRules(state: GameFlowRulesState) {
        val ruleContext = GameRuleContext(model, eliminations, replayable)
        setupContext.flowRulesDsl?.invoke(GameFlowRulesContext(ruleContext, state, null, this))
    }

    override fun <E> fire(executor: GameEvents<E>, event: E) {
        val ruleContext = GameRuleContext(model, eliminations, replayable)
        val context = GameFlowRulesContext(ruleContext, GameFlowRulesState.FIRE_EVENT,
        executor as GameEvents<*> to event as Any, this)
        setupContext.flowRulesDsl?.invoke(context)
        context.fire(executor, event)
    }

    private fun clear() {
        this.views.clear()
        this.actions.clear()
    }

    override fun view(key: String, value: ViewScope<T>.() -> Any?) {
        views.add(key to value)
    }

    override fun <A : Any> action(action: ActionType<T, A>, actionDsl: GameFlowActionScope<T, A>.() -> Unit) {
        actions.add(action, actionDsl)
    }

    override fun <E : Any> config(config: GameConfig<E>): E = this.gameConfig.get(config)

    // current possible actions, cleared and re-filled after every step
    // a coroutine to keep the game running. Cancellable?
    // an indicator if the game is running or not
    // copy-able, for some AIs

    // rules that are checked after every move, and/or before sending responses back
    // view, cleared and re-filled after every step - can be filled by rules
    // events, can be executed at any time and can modify view and actions

}

class GameFlowContext<T: Any>(
    private val coroutineScope: CoroutineScope,
    val flow: GameFlowImpl<T>,
    private val name: String
): GameFlowScope<T>, GameFlowStepScope<T> {
    override val game: T get() = flow.model
    override val eliminations: PlayerEliminationsWrite get() = flow.eliminations
    override val replayable: ReplayState get() = flow.replayable

    override suspend fun loop(function: suspend GameFlowScope<T>.() -> Unit) {
        while (!flow.isGameOver()) {
            function.invoke(this)
        }
    }

    override suspend fun step(name: String, step: suspend GameFlowStepScope<T>.() -> Unit): GameFlowStep<T> {
        val impl = GameFlowStepImpl(flow, coroutineScope, "${this.name}/$name", step)
        impl.runDsl()
        return impl
    }

    override fun <A : Any> yieldAction(action: ActionType<T, A>, actionDsl: GameFlowActionScope<T, A>.() -> Unit) {
        flow.actions.add(action, actionDsl)
    }

    override fun yieldView(key: String, value: ViewScope<T>.() -> Any?) { flow.view(key, value) }

    override suspend fun log(logging: LogScope<T>.() -> String) {
        replayable.stateKeeper.log(LogContext(game, null).log(logging))
    }

    override suspend fun logSecret(player: PlayerIndex, logging: LogScope<T>.() -> String): LogSecretScope<T> {
        val context = LogContext(game, player).secretLog(player, logging)
        replayable.stateKeeper.log(context)
        return context
    }

    override fun <A : Any> enableAction(actionDefinition: ActionDefinition<T, A>)
        = yieldAction(actionDefinition.actionType, actionDefinition.actionDsl)
    override fun <E : Any> config(gameConfig: GameConfig<E>): E = flow.gameConfig.get(gameConfig)
}

@GameMarker
interface GameFlowActionScope<T: Any, A: Any> {
    // fun appliesForActions(condition: ActionRuleScope<T, A>.() -> Boolean) // TODO: Allow dynamically modify some actions, such as "If you do this it costs 3 gold"
    fun after(rule: ActionRuleScope<T, A>.() -> Unit)
    fun perform(rule: ActionRuleScope<T, A>.() -> Unit)
    fun precondition(rule: ActionOptionsScope<T>.() -> Boolean)
    fun requires(rule: ActionRuleScope<T, A>.() -> Boolean)
    fun options(rule: ActionOptionsScope<T>.() -> Iterable<A>)
    fun choose(options: ActionChoicesScope<T, A>.() -> Unit)
}
interface ActionDefinition<T: Any, A: Any> {
    val actionType: ActionType<T, A>
    val actionDsl: GameFlowActionScope<T, A>.() -> Unit
}
@GameMarker
interface GameFlowStepScope<T: Any> {
    val game: T
    val eliminations: PlayerEliminationsWrite
    val replayable: ReplayableScope
    fun <A: Any> enableAction(actionDefinition: ActionDefinition<T, A>)
    fun <A: Any> yieldAction(action: ActionType<T, A>, actionDsl: GameFlowActionDsl<T, A>)
    fun yieldView(key: String, value: ViewScope<T>.() -> Any?)
    suspend fun log(logging: LogScope<T>.() -> String)
    suspend fun logSecret(player: PlayerIndex, logging: LogScope<T>.() -> String): LogSecretScope<T>
}
@GameMarker
interface GameFlowScope<T: Any>: EventTools {
    val game: T
    override val eliminations: PlayerEliminationsWrite
    override val replayable: ReplayableScope
    suspend fun loop(function: suspend GameFlowScope<T>.() -> Unit)
    suspend fun step(name: String, step: suspend GameFlowStepScope<T>.() -> Unit): GameFlowStep<T>
    suspend fun log(logging: LogScope<T>.() -> String)
    suspend fun logSecret(player: PlayerIndex, logging: LogScope<T>.() -> String): LogSecretScope<T>
}

interface GameFlowStep<T: Any> {
    val action: Actionable<T, Any>?
    suspend fun loopUntil(function: GameFlowStep<T>.() -> Boolean)
}