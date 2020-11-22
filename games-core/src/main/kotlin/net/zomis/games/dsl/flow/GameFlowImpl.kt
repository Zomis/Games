package net.zomis.games.dsl.flow

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
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
): Game<T>, GameFactoryScope<Any>, GameEventsExecutor {
    private val mainScope = MainScope()
    val views = mutableListOf<Pair<String, ViewScope<T>.() -> Any?>>()
    val feedbackOutput = Channel<Any>()
    private val feedbacks = mutableListOf<GameFlowContext.Steps.FlowStep>()

    override val eliminations: PlayerEliminations = PlayerEliminations(playerCount).also {
        it.callback = { elimination ->
            feedbacks.add(GameFlowContext.Steps.Elimination(elimination))
            if (it.isGameOver()) feedbacks.add(GameFlowContext.Steps.GameEnd)
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
            val dsl = setupContext.flowDsl!!
            val flowContext = GameFlowContext(this, game, "root", null)
            dsl.invoke(flowContext)
            sendFeedbacks()
            println("MainScope done for $game")
        }
    }

    suspend fun sendFeedbacks() {
        feedbacks.forEach {
            feedbackOutput.send(it)
        }
        stateKeeper.logs().forEach {
            feedbackOutput.send(GameFlowContext.Steps.Log(it))
        }
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
        require(duplicates.isEmpty()) { "Multiple keys detected in view of: $duplicates" }
        val viewContext = GameViewContext(model, eliminations, playerIndex)
        return this.views.associate { it.first to it.second(viewContext) }
    }

    override fun viewRequest(playerIndex: PlayerIndex, key: String, params: Map<String, Any>): Any? {
        TODO("Not yet implemented")
    }

    suspend fun nextAction() {
        runRules(GameFlowRulesState.BEFORE_RETURN)
        sendFeedbacks()
        val action = actionsInput.receive()
        replayable.stateKeeper.clear()
        require(action is Actionable<*, *>)
        println("Action Received: $action")
        val typeEntry = actions.type(action.actionType)
        if (typeEntry == null) {
            feedbackOutput.send(GameFlowContext.Steps.IllegalAction(action.actionType, action.playerIndex, action.parameter))
            return
        }
        if (typeEntry.isAllowed(action as Actionable<T, Any>)) {
            this.clear()
        }
        typeEntry.perform(action as Actionable<T, Any>)
        feedbackOutput.send(GameFlowContext.Steps.ActionPerformed(action.actionType, action.playerIndex, action.parameter))
        runRules(GameFlowRulesState.AFTER_ACTIONS)
    }

    private fun runRules(state: GameFlowRulesState) {
        val ruleContext = GameRuleContext(model, eliminations, replayable)
        setupContext.flowRulesDsl?.invoke(GameFlowRulesContext(ruleContext, state, null) { feedbacks.add(it) })
    }

    override fun <E> fire(executor: GameEvents<E>, event: E) {
        val ruleContext = GameRuleContext(model, eliminations, replayable)
        val context = GameFlowRulesContext(ruleContext, GameFlowRulesState.FIRE_EVENT,
        executor as GameEvents<*> to event as Any
        ) { feedbacks.add(it) }
        context.fire(executor, event)
    }

    private fun clear() {
        this.views.clear()
        this.actions.clear()
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
    private val name: String,
    private val parent: GameFlowContext<T>?
): GameFlowScope<T>, GameFlowStepScope<T> {
    override val game: T get() = flow.model

    object Steps {
        interface FlowStep
        object GameEnd: FlowStep
        data class Elimination(val elimination: PlayerElimination): FlowStep
        data class ActionPerformed(val actionType: String, val playerIndex: Int, val parameter: Any): FlowStep
        data class IllegalAction(val actionType: String, val playerIndex: Int, val parameter: Any): FlowStep
        data class Log(val log: ActionLogEntry): FlowStep
        data class RuleExecution(val ruleName: String, val values: Any): FlowStep
        object AwaitInput: FlowStep
    }

    override suspend fun loop(function: suspend GameFlowScope<T>.() -> Unit) {
        while (!flow.isGameOver()) {
            function.invoke(this)
        }
    }

    override suspend fun step(name: String, step: suspend GameFlowStepScope<T>.() -> Unit) {
        if (flow.isGameOver()) return
        println("step $name")
        val child = GameFlowContext(coroutineScope, flow, "${this.name}/$name", this)
        step.invoke(child)
        println("awaiting actionInput")
        flow.sendFeedbacks()
        flow.feedbackOutput.send(Steps.AwaitInput)
        flow.nextAction()
    }

    override fun <A : Any> yieldAction(playerIndex: Int, action: ActionType<T, A>, actionDsl: GameFlowActionScope<T, A>.() -> Unit) {
        flow.actions.add(action, actionDsl)
    }

    override fun yieldView(key: String, value: ViewScope<T>.() -> Any?) { flow.views.add(key to value) }

    override suspend fun log(logging: LogScope<T>.() -> String) {
        TODO("Not yet implemented")
    }

    override suspend fun logSecret(player: PlayerIndex, logging: LogScope<T>.() -> String): LogSecretScope<T> {
        TODO("Not yet implemented")
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
    fun choose(options: ActionChoicesStartScope<T, A>.() -> Unit)
}
@GameMarker
interface GameFlowStepScope<T: Any> {
    val game: T
    fun <A: Any> yieldAction(playerIndex: Int, action: ActionType<T, A>, actionDsl: GameFlowActionScope<T, A>.() -> Unit)
    fun yieldView(key: String, value: ViewScope<T>.() -> Any?)
}
@GameMarker
interface GameFlowScope<T: Any> {
    val game: T
    suspend fun loop(function: suspend GameFlowScope<T>.() -> Unit)
    suspend fun step(name: String, step: suspend GameFlowStepScope<T>.() -> Unit)
    suspend fun log(logging: LogScope<T>.() -> String)
    suspend fun logSecret(player: PlayerIndex, logging: LogScope<T>.() -> String): LogSecretScope<T>
}
