package net.zomis.games.dsl.flow

import klog.KLoggers
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import net.zomis.games.PlayerEliminations
import net.zomis.games.PlayerEliminationsWrite
import net.zomis.games.api.UsageScope
import net.zomis.games.common.PlayerIndex
import net.zomis.games.dsl.*
import net.zomis.games.dsl.events.EventSource
import net.zomis.games.dsl.events.EventsHandling
import net.zomis.games.dsl.flow.actions.SmartActionBuilder
import net.zomis.games.dsl.flow.actions.SmartActionContext
import net.zomis.games.dsl.flow.actions.SmartActionScope
import net.zomis.games.dsl.impl.*
import net.zomis.games.rules.ActiveRules
import net.zomis.games.rules.NoState
import net.zomis.games.rules.Rule
import net.zomis.games.rules.StandaloneStateOwner

private const val ECS_VIEW_KEY = ""
const val VIEWMODEL_VIEW_KEY = "_vm"

class GameFlowImpl<T: Any>(
    private val setupContext: GameDslContext<T>,
    override val playerCount: Int,
    val gameConfig: GameConfigs,
    private val copier: suspend (FlowStep.RandomnessResult?) -> GameForkResult<T>,
    val forkedGame: () -> Boolean
): Game<T>, GameFactoryScope<T, Any>, GameFlowRuleCallbacks<T>, GameMetaScope<T> {
    override val configs: GameConfigs get() = gameConfig

    private val activeRules = ActiveRules<T>(this)
    private val stateKeeper = StateKeeper()
    override val gameType: String = setupContext.gameType
    override fun toString(): String = "${super.toString()}-$gameType"

    @Deprecated("use config method instead")
    override val config: Any get() = gameConfig.oldStyleValue()
    private val logger = KLoggers.logger(this)
    private var unfinishedFeedback: FlowStep.RandomnessResult? = null
    val views = mutableListOf<Pair<String, ViewScope<T>.() -> Any?>>()
    private var viewModel: ViewModel<T, *>? = null
    override val feedbackFlow: Channel<FlowStep> = Channel()
    private var onNoActions: () -> Unit = {}

    private val feedbacks = mutableListOf<FlowStep>()

    override val eliminations: PlayerEliminations = PlayerEliminations(playerCount).also {
        it.callback = { elimination ->
            feedbacks.add(FlowStep.Elimination(elimination))
        }
    }

    override fun <A : Any> addAction(actionType: ActionType<T, A>, handler: SmartActionBuilder<T, A>) {
        this.actions.add(actionType, handler)
    }

    override fun <A : Any> addActionHandler(actionType: ActionType<T, A>, dsl: SmartActionScope<T, A>.() -> Unit) {
        val smartActionContext = SmartActionContext(actionType, this)
        dsl.invoke(smartActionContext)
        this.actions.add(actionType, smartActionContext)
    }

    override fun <A : Any> addAction(actionType: ActionType<T, A>, actionDsl: GameFlowActionDsl<T, A>) {
        this.actions.add(actionType, actionDsl)
    }

    override fun addGlobalActionPrecondition(rule: ActionOptionsScope<T>.() -> Boolean) {
        this.actions.addGlobalPrecondition(rule)
    }

    override val events: EventsHandling<T> = EventsHandling(this)
    override val eliminationCallback: PlayerEliminationsWrite = eliminations
    override val model: T = setupContext.model.factory(this) // TODO: Maybe this should happen last?
    override val replayable = ReplayState(stateKeeper)
    override val actions = GameFlowActionsImpl({ feedbacks.add(it) }, this)
    override val feedback: (FlowStep) -> Unit = { feedbacks.add(it) }
    private val rules: MutableList<GameModifierImpl<T, out Any?>> = mutableListOf()
    override fun <E : Any> fireEvent(source: EventSource, event: E, performEvent: (E) -> Unit) {
        logger.info { "fireEvent from source $source with value $event" }
        this.events.fireEvent(source, event, performEvent as (Any) -> Unit)
    }

    override val meta: GameMetaScope<T> get() = this
    private val baseRule = Rule(this, Unit, setupContext.getBaseRule(model) ?: {}, NoState)

    override val actionsInput: Channel<Actionable<T, out Any>> = Channel()
    var job: Job? = null
    override suspend fun start(coroutineScope: CoroutineScope) {
        if (job != null) throw IllegalStateException("Game already started")
        val game = this
        job = coroutineScope.launch(Dispatchers.Default + CoroutineName("Job for game $this")) {
            try {
                sendFeedback(FlowStep.GameStarted(game, gameConfig))
                val dsl = setupContext.flowDsl!!
                val flowContext = GameFlowContext(this, game, "root")
                replayable.stateKeeper.preSetup(game) { sendFeedback(it) }
                setupContext.model.onStart(GameStartContext(gameConfig, model, replayable, playerCount))
                game.unfinishedFeedback = FlowStep.GameSetup(game, gameConfig, replayable.stateKeeper.lastMoveState())
                dsl.invoke(flowContext)
                runRules(GameFlowRulesState.BEFORE_RETURN)
                actionDone()
                sendFeedback(FlowStep.GameEnd)
            } catch (e: CancellationException) {
                if (e.cause != null) logger.warn(e) { "Game cancelled: $game" }
                else logger.info { "Game cancelled (no cause): $game" }
            } catch (e: Exception) {
                logger.error(e) { "Error in Coroutine for game $game" }
            }
        }
    }

    override fun onNoActions(function: () -> Unit) {
        val old = this.onNoActions
        this.onNoActions = {
            old.invoke()
            function.invoke()
        }
    }

    override fun <Owner> addRule(owner: Owner, rule: GameModifierScope<T, Owner>.() -> Unit) {
        val ruleContext = GameModifierImpl(this, owner, rule, StandaloneStateOwner())
        ruleContext.fire()
        this.rules.add(ruleContext)
        ruleContext.executeOnActivate()
    }

    override fun <Owner> removeRule(rule: GameModifierScope<T, Owner>) {
        val remove = this.rules.single { it == rule }
        this.rules.remove(remove)
    }

    override fun stop() {
        this.feedbackFlow.close()
        this.job?.cancel()
        this.job = null
    }

    suspend fun sendFeedbacks() {
        feedbacks.toList().forEach {
            sendFeedback(it)
        }
        stateKeeper.logs().toList().forEach {
            sendFeedback(FlowStep.Log(it))
        }
        stateKeeper.clearLogs()
        feedbacks.clear()
    }

    internal val stepInjectionQueue = mutableListOf<GameFlowStep<T>>()
    override fun injectStep(name: String, dsl: suspend GameFlowStepScope<T>.() -> Unit) {
        logger.info { "Add step to queue: $name" }
        this.stepInjectionQueue.add(GameFlowStep(name, dsl))
    }

    override suspend fun copy(): GameForkResult<T> {
        return when (val unfinished = this.unfinishedFeedback) {
            null -> copier.invoke(null)
            is FlowStep.GameSetup<*> -> copier.invoke(unfinished.copy(state = replayable.stateKeeper.lastMoveState()))
            is FlowStep.ActionPerformed<*> -> copier.invoke(unfinished.copy(state = replayable.stateKeeper.lastMoveState()))
            else -> throw UnsupportedOperationException("Unsupported unfinished feedback: $unfinished")
        }
    }

    override fun view(playerIndex: PlayerIndex): Map<String, Any?> {
        val duplicates = views.map { it.first }.groupingBy { it }.eachCount().filter { it.value > 1 }
        if (duplicates.isNotEmpty()) logger.warn {  "Multiple keys detected in view of: $duplicates" }
        val viewContext = GameViewContext(this, playerIndex)
        val result = this.views.associate { it.first to it.second(viewContext) }.filterValues { it !is HiddenValue }

        val singleView = result.entries.singleOrNull()
        // A bit of an ugly hack for if the full view is specified
        return when (singleView?.key) {
            ECS_VIEW_KEY -> result.getValue(ECS_VIEW_KEY) as Map<String, Any?> // Introduced for ECS-style games.
            else -> result
        }
    }
    private fun copyUnfinishedFeedbackWithUpdatedState(): FlowStep.RandomnessResult? {
        return when (val unfinished = unfinishedFeedback) {
            null -> null
            is FlowStep.GameSetup<*> -> unfinished.copy(state = replayable.stateKeeper.lastMoveState())
            is FlowStep.ActionPerformed<*> -> unfinished.copy(state = replayable.stateKeeper.lastMoveState())
            else -> throw UnsupportedOperationException("Unsupported unfinished feedback: $unfinished")
        }
    }

    suspend fun nextAction(): Actionable<T, Any>? {
        this.unfinishedFeedback = copyUnfinishedFeedbackWithUpdatedState()
        activeRules.fireRules(baseRule)
        runRules(GameFlowRulesState.BEFORE_RETURN)
        if (isGameOver()) {
            this.actionDone()
            return null
        }
        actions.types().forEach { it.impl.ruleChecks() }
        if (anyPlayerHasAction()) {
            this.actionDone()
            while (true) {
                if (isGameOver()) {
                    return null
                }
                replayable.stateKeeper.clear()
                sendFeedback(FlowStep.AwaitInput)
                val action = actionsInput.receive()
                logger.info("GameFlow Coroutine Action Received: $action")
                val typeEntry = actions.type(action.actionType)
                if (typeEntry == null) {
                    sendFeedback(FlowStep.IllegalAction(action, ActionResult(action, null).also { it.addPrecondition("actionType entry exists", null, false) }))
                    continue
                }
                replayable.stateKeeper.preMove(action) { sendFeedback(it) }
                logger.info("clear and perform: ${replayable.stateKeeper.lastMoveState()}")
                val performed = actions.clearAndPerform(action as Actionable<T, Any>) {
                    this.clear()
                }
                if (!performed.allowed) {
                    sendFeedback(FlowStep.IllegalAction(action, typeEntry.impl.checkAllowed(action)))
                    logger.warn { "Action not allowed: $action" }
                    continue
                }
                logger.info("creating last action: ${replayable.stateKeeper.lastMoveState()}")
                val last = FlowStep.ActionPerformed(action, typeEntry.actionType, replayable.stateKeeper.lastMoveState(), performed)
                this.unfinishedFeedback = last
                logger.info("last action is: $last")
                runRules(GameFlowRulesState.AFTER_ACTIONS)
                return action
            }
        } else {
            logger.info("GameFlow Coroutine No Available Actions. ActionTypes: ${actions.types().map { it.name }}")
            sendFeedback(FlowStep.NextView)
            onNoActions.invoke()
            clear()
            runRules(GameFlowRulesState.AFTER_ACTIONS)
            return null
        }
    }

    private suspend fun actionDone() {
        sendFeedbacks()
        unfinishedFeedback?.also { sendFeedback(it as FlowStep) }
        unfinishedFeedback = null
    }

    private fun anyPlayerHasAction(): Boolean {
        // Check if any player can do any action, anywhere
        return eliminations.playerIndices.any { playerIndex ->
            actions.types().any { action -> action.availableActions(playerIndex, null).any() }
        }
    }

    private suspend fun sendFeedback(feedback: FlowStep) {
        logger.info("GameFlow Coroutine sends feedback: $feedback")
        this.feedbackFlow.send(feedback)
        // logger.info("GameFlow Coroutine feedback sent: $feedback, continuing coroutine...") // Possible ConcurrentModificationExceptions
    }

    private fun runRules(state: GameFlowRulesState) {
        setupContext.flowRulesDsl?.invoke(GameFlowRulesContext(this, state, this))
        when (state) {
            GameFlowRulesState.AFTER_ACTIONS -> { /* ignore */ }
            GameFlowRulesState.BEFORE_RETURN -> this.rules.toList().forEach { it.executeBeforeAction() }
            GameFlowRulesState.FIRE_EVENT -> { /* ignore */ }
        }
    }

    private fun clear() {
        this.onNoActions = {}
        this.views.clear()
        this.actions.clear()
    }

    override fun view(key: String, value: ViewScope<T>.() -> Any?) {
        views.add(key to value)
    }

    override fun viewModel(viewModel: ViewModel<T, *>) {
        views.add(VIEWMODEL_VIEW_KEY to {
            viewModel.factory.invoke(this.game, this.viewer ?: -1)
        })
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
    override val meta: GameMetaScope<T> get() = flow
    override val eliminations: PlayerEliminationsWrite get() = flow.eliminations
    override val replayable: ReplayState get() = flow.replayable

    override suspend fun forkGame(actions: suspend GameForkScope<T>.() -> Unit): GameFork<T>? {
        if (flow.forkedGame.invoke()) {
            println("Fork $flow (${flow.model}) is a forked game, preventing further forks.")
            return null
        }
        val copy = flow.copy()
        val fork = GameForkContext(copy)
        actions.invoke(fork)
        copy.blockingGameListener.await()
        copy.allowForks = true
        copy.game.stop()
        return copy.game
    }

    override suspend fun loop(function: suspend GameFlowScope<T>.() -> Unit) {
        while (!flow.isGameOver()) {
            function.invoke(this)
        }
    }

    override suspend fun step(name: String, dsl: suspend GameFlowStepScope<T>.() -> Unit): GameFlowStepResult<T> {
        println("Run step $name queue is ${flow.stepInjectionQueue.size}")
        while (flow.stepInjectionQueue.isNotEmpty()) {
            val step = flow.stepInjectionQueue.removeFirst()
            println("Run queued step ${step.name}")
            val impl = GameFlowStepImpl(flow, coroutineScope, "${this.name}/$name", step)
            impl.runDsl()
        }

        println("Run step $name queue is ${flow.stepInjectionQueue.size}")
        val step = GameFlowStep(name, dsl)
        val impl = GameFlowStepImpl(flow, coroutineScope, "${this.name}/$name", step)
        impl.runDsl()
        return impl
    }

    override fun <A : Any> yieldAction(action: ActionType<T, A>, actionDsl: GameFlowActionScope<T, A>.() -> Unit) {
        flow.actions.add(action, actionDsl)
    }

    override fun <A : Any> actionHandler(action: ActionType<T, A>, dsl: SmartActionScope<T, A>.() -> Unit) {
        flow.addActionHandler(action, dsl)
    }

    override fun <A : Any> actionHandler(action: ActionType<T, A>, handler: SmartActionBuilder<T, A>) {
        flow.actions.add(action, handler)
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
interface GameFlowActionScope<T: Any, A: Any> : UsageScope {
    // fun appliesForActions(condition: ActionRuleScope<T, A>.() -> Boolean) // TODO: Allow dynamically modify some actions, such as "If you do this it costs 3 gold"
    fun after(rule: ActionRuleScope<T, A>.() -> Unit)
    fun perform(rule: ActionRuleScope<T, A>.() -> Unit)
    fun precondition(rule: ActionOptionsScope<T>.() -> Boolean)
    fun preconditionDenyIf(rule: ActionOptionsScope<T>.() -> Boolean)
    fun requires(rule: ActionRuleScope<T, A>.() -> Boolean)
    fun options(rule: ActionOptionsScope<T>.() -> Iterable<A>)
    fun exampleOptions(rule: ActionOptionsScope<T>.() -> Iterable<A>)
    fun choose(options: ActionChoicesScope<T, A>.() -> Unit)
}
interface ActionDefinition<T: Any, A: Any> {
    val actionType: ActionType<T, A>
    val actionDsl: GameFlowActionScope<T, A>.() -> Unit
}
typealias SmartActionDsl<T, A> = SmartActionScope<T, A>.() -> Unit
@GameMarker
interface GameFlowStepScope<T: Any> : UsageScope {
    val game: T
    val eliminations: PlayerEliminationsWrite
    val replayable: ReplayStateI
    suspend fun forkGame(actions: suspend GameForkScope<T>.() -> Unit = {}): GameFork<T>?
    fun <A: Any> enableAction(actionDefinition: ActionDefinition<T, A>)
    fun <A: Any> yieldAction(action: ActionType<T, A>, actionDsl: GameFlowActionDsl<T, A>)
    fun <A: Any> actionHandler(action: ActionType<T, A>, handler: SmartActionBuilder<T, A>)
    fun <A: Any> actionHandler(action: ActionType<T, A>, dsl: SmartActionScope<T, A>.() -> Unit)
    fun yieldView(key: String, value: ViewScope<T>.() -> Any?)
    suspend fun log(logging: LogScope<T>.() -> String)
    suspend fun logSecret(player: PlayerIndex, logging: LogScope<T>.() -> String): LogSecretScope<T>
}
@GameMarker
interface GameFlowScope<T: Any>: EventTools, UsageScope {
    val game: T
    val meta: GameMetaScope<T>
    override val eliminations: PlayerEliminationsWrite
    override val replayable: ReplayStateI
    suspend fun loop(function: suspend GameFlowScope<T>.() -> Unit)
    suspend fun step(name: String, dsl: suspend GameFlowStepScope<T>.() -> Unit): GameFlowStepResult<T>
    suspend fun log(logging: LogScope<T>.() -> String)
    suspend fun logSecret(player: PlayerIndex, logging: LogScope<T>.() -> String): LogSecretScope<T>
}

class GameFlowStep<GameModel: Any>(val name: String, val dsl: suspend GameFlowStepScope<GameModel>.() -> Unit)
interface GameFlowStepResult<T: Any> {
    val action: Actionable<T, Any>?
    suspend fun loopUntil(function: GameFlowStepResult<T>.() -> Boolean)
}
