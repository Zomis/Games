package net.zomis.games.dsl.flow

import klog.KLoggers
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import net.zomis.games.PlayerElimination
import net.zomis.games.PlayerEliminationCallback
import net.zomis.games.PlayerEliminations
import net.zomis.games.common.GameEvents
import net.zomis.games.common.PlayerIndex
import net.zomis.games.dsl.*
import net.zomis.games.dsl.impl.*

class GameFlowImpl<T: Any>(
    private val setupContext: GameDslContext<T>,
    override val playerCount: Int,
    override val config: Any,
    override val stateKeeper: StateKeeper
): Game<T>, GameFactoryScope<Any>, GameEventsExecutor, GameFlowRuleCallbacks<T> {
    private val logger = KLoggers.logger(this)
    private var lastAction: GameFlowContext.Steps.ActionPerformed<T>? = null
    private val mainScope = MainScope()
    val views = mutableListOf<Pair<String, ViewScope<T>.() -> Any?>>()
    private val feedbackOutput = Channel<GameFlowContext.Steps.FlowStep>()
    val feedbackReceiver: ReceiveChannel<GameFlowContext.Steps.FlowStep> get() = feedbackOutput
    private val feedbacks = mutableListOf<GameFlowContext.Steps.FlowStep>()

    override val eliminations: PlayerEliminations = PlayerEliminations(playerCount).also {
        it.callback = { elimination ->
            feedbacks.add(GameFlowContext.Steps.Elimination(elimination))
        }
    }
    override val model: T = setupContext.model.factory(this, config)
    val replayable = ReplayState(stateKeeper, eliminations)
    override val actions = GameFlowActionsImpl({ feedbacks.add(it) }, model, eliminations, replayable)
    override val events: GameEventsExecutor = this
    override val eliminationCallback: PlayerEliminationCallback = eliminations

    val actionsInput: Channel<Any> = Channel()
    val job: Job
    init {
        val game = this
        job = mainScope.launch(Dispatchers.Default) {
            println("GameFlow Coroutine started")
            try {
                val dsl = setupContext.flowDsl!!
                val flowContext = GameFlowContext(this, game, "root")
                setupContext.model.onStart(GameStartContext(model, replayable))
                sendFeedback(GameFlowContext.Steps.GameSetup(playerCount, config, replayable.stateKeeper.lastMoveState()))
                dsl.invoke(flowContext)
                actionDone()
                sendFeedback(GameFlowContext.Steps.GameEnd)
                println("GameFlow Coroutine MainScope done for $game")
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
            println("GameFlow Coroutine Feedback StateKeeper saved Log: $it")
            sendFeedback(GameFlowContext.Steps.Log(it))
        }
        stateKeeper.clearLogs()
        println("GameFlow Coroutine Feedbacks clear")
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
        if (duplicates.isEmpty()) logger.warn {  "Multiple keys detected in view of: $duplicates" }
        val viewContext = GameViewContext(model, eliminations, playerIndex)
        return this.views.associate { it.first to it.second(viewContext) }
    }

    override fun viewRequest(playerIndex: PlayerIndex, key: String, params: Map<String, Any>): Any? {
        TODO("Not yet implemented")
    }

    suspend fun nextAction(): Actionable<T, Any>? {
        if (isGameOver()) {
            this.actionDone()
            return null
        }
        if (anyPlayerHasAction()) {
            this.actionDone()
            while (true) {
                sendFeedback(GameFlowContext.Steps.AwaitInput)
                val action = actionsInput.receive()
                println("GameFlow Coroutine Action Received: $action")
                replayable.stateKeeper.clear()
                require(action is Actionable<*, *>)
                val typeEntry = actions.type(action.actionType)
                if (typeEntry == null) {
                    sendFeedback(GameFlowContext.Steps.IllegalAction(action.actionType, action.playerIndex, action.parameter))
                    continue
                }
                actions.clearAndPerform(action as Actionable<T, Any>) { this.clear() }
                this.lastAction = GameFlowContext.Steps.ActionPerformed(typeEntry, action.playerIndex, action.parameter)
                runRules(GameFlowRulesState.AFTER_ACTIONS)
                return action
            }
        } else {
            println("GameFlow Coroutine No Available Actions")
            sendFeedback(GameFlowContext.Steps.NextView)
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

    suspend fun sendFeedback(feedback: GameFlowContext.Steps.FlowStep) {
        println("GameFlow Coroutine sends feedback: $feedback")
        this.feedbackOutput.send(feedback)
        println("GameFlow Coroutine feedback sent: $feedback, continuing coroutine...")
    }

    private fun runRules(state: GameFlowRulesState) {
        val ruleContext = GameRuleContext(model, eliminations, replayable)
        setupContext.flowRulesDsl?.invoke(GameFlowRulesContext(ruleContext, state, null, this))
    }

    override fun <E> fire(executor: GameEvents<E>, event: E) {
        val ruleContext = GameRuleContext(model, eliminations, replayable)
        val context = GameFlowRulesContext(ruleContext, GameFlowRulesState.FIRE_EVENT,
        executor as GameEvents<*> to event as Any, this)
        context.fire(executor, event)
    }

    private fun clear() {
        println("GameFlow Clear views and actions")
        this.views.clear()
        this.actions.clear()
    }

    override fun view(key: String, value: ViewScope<T>.() -> Any?) {
        println("GameFlow Add view $key")
        views.add(key to value)
    }

    override val feedback: (GameFlowContext.Steps.FlowStep) -> Unit = { feedbacks.add(it) }
    override fun <A : Any> action(action: ActionType<T, A>, actionDsl: GameFlowActionScope<T, A>.() -> Unit) {
        actions.add(action, actionDsl)
    }

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
    override val eliminations: PlayerEliminationCallback get() = flow.eliminations
    override val replayable: ReplayState get() = flow.replayable

    object Steps {
        interface FlowStep
        object GameEnd: FlowStep
        data class Elimination(val elimination: PlayerElimination): FlowStep
        data class ActionPerformed<T: Any>(val actionImpl: ActionTypeImplEntry<T, Any>, val playerIndex: Int, val parameter: Any): FlowStep
        data class IllegalAction(val actionType: String, val playerIndex: Int, val parameter: Any): FlowStep
        data class Log(val log: ActionLogEntry): FlowStep
        data class RuleExecution(val ruleName: String, val values: Any): FlowStep
        data class GameSetup(val playerCount: Int, val config: Any, val state: Map<String, Any>): FlowStep
        object AwaitInput: FlowStep
        object NextView : FlowStep
    }

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
@GameMarker
interface GameFlowStepScope<T: Any> {
    val game: T
    val eliminations: PlayerEliminationCallback
    val replayable: ReplayableScope
    fun <A: Any> yieldAction(action: ActionType<T, A>, actionDsl: GameFlowActionDsl<T, A>)
    fun yieldView(key: String, value: ViewScope<T>.() -> Any?)
    suspend fun log(logging: LogScope<T>.() -> String)
    suspend fun logSecret(player: PlayerIndex, logging: LogScope<T>.() -> String): LogSecretScope<T>
}
@GameMarker
interface GameFlowScope<T: Any> {
    val game: T
    val eliminations: PlayerEliminationCallback
    suspend fun loop(function: suspend GameFlowScope<T>.() -> Unit)
    suspend fun step(name: String, step: suspend GameFlowStepScope<T>.() -> Unit): GameFlowStep<T>
    suspend fun log(logging: LogScope<T>.() -> String)
    suspend fun logSecret(player: PlayerIndex, logging: LogScope<T>.() -> String): LogSecretScope<T>
}

interface GameFlowStep<T: Any> {
    val action: Actionable<T, Any>?
    suspend fun loopUntil(function: GameFlowStep<T>.() -> Boolean)
}
