package net.zomis.games.dsl.flow.actions

import klog.KLoggers
import net.zomis.games.dsl.*
import net.zomis.games.dsl.flow.GameFlowActionContext
import net.zomis.games.dsl.flow.GameFlowActionDsl
import net.zomis.games.dsl.impl.*

private class GameFlowActionContextPrecondition<T: Any, A: Any>(private val context: ActionOptionsContext<T>): GameFlowActionContext<T, A>() {
    var result = true
    override fun precondition(rule: ActionOptionsScope<T>.() -> Boolean) {
        result = result && rule.invoke(context)
    }
}
private class GameFlowActionContextRequires<T: Any, A: Any>(private val context: ActionRuleContext<T, A>): GameFlowActionContext<T, A>() {
    var result = true
    override fun precondition(rule: ActionOptionsScope<T>.() -> Boolean) {
        result = result && rule.invoke(context)
    }

    override fun requires(rule: ActionRuleScope<T, A>.() -> Boolean) {
        result = result && rule.invoke(context)
    }
}
private class GameFlowActionContextOptions<T: Any, A: Any>(
    private val context: ActionOptionsContext<T>,
    private val actionType: ActionType<T, A>,
    private val sampleSize: ActionSampleSize?
): GameFlowActionContext<T, A>() {
    private var iterable: Iterable<A>? = null
    init {
        if (actionType.serializedType == Unit::class) {
            iterable = listOf(Unit as A)
        }
    }
    override fun options(rule: ActionOptionsScope<T>.() -> Iterable<A>) {
        check(this.iterable == null) { "options and/or choices can only be defined once" }
        this.iterable = rule.invoke(context)
    }

    override fun choose(options: ActionChoicesStartScope<T, A>.() -> Unit) {
        check(this.iterable == null) { "options and/or choices can only be defined once" }
        val complex = RulesActionTypeComplex(context, actionType, options)
        val actions = complex.availableActions(sampleSize).map { it.parameter }
        this.iterable = actions.toList()
    }

    fun iterable(): Iterable<A> = iterable ?: emptyList()
}

private class GameFlowActionContextKeys<T: Any, A: Any>(
    private val context: ActionOptionsContext<T>,
    private val actionType: ActionType<T, A>,
    private val previouslySelected: List<Any>,
    private val allowedCheck: (Actionable<T, A>) -> Boolean
): GameFlowActionContext<T, A>() {
    private var initialized: Boolean = false
    private fun initialize() {
        require(!initialized) { "options and/or choices can only be defined once" }
        initialized = true
    }

    private fun actionInfoKey(actionable: Actionable<T, A>)
        = ActionInfoKey(actionType.serialize(actionable.parameter), actionType.name, emptyList(), true)
    val actionInfoKeys = mutableListOf<ActionInfoKey>()
    init {
        if (actionType.serializedType == Unit::class) {
            actionInfoKeys.add(ActionInfoKey(Unit, actionType.name, emptyList(), true))
        }
    }

    override fun options(rule: ActionOptionsScope<T>.() -> Iterable<A>) {
        initialize()
        actionInfoKeys.addAll(rule.invoke(context)
            .map { context.createAction(it) }
            .filter { allowedCheck(it) }
            .map(this::actionInfoKey))
    }

    override fun choose(options: ActionChoicesStartScope<T, A>.() -> Unit) {
        initialize()
        val complex = RulesActionTypeComplex(context, actionType, options)
        actionInfoKeys.addAll(complex.availableActionKeys(previouslySelected))
    }

}

class GameFlowLogicActionAvailable<T: Any, A: Any>(
    private val gameData: GameRuleContext<T>,
    private val actionType: ActionType<T, A>,
    private val actionDsls: () -> List<GameFlowActionDsl<T, A>>
): GameFlowActionContext<T, A>() {
    private val logger = KLoggers.logger(this)
    private fun createContext(action: Actionable<T, A>)
        = ActionRuleContext(gameData.game, action, gameData.eliminations, gameData.replayable)

    private fun checkPreconditions(playerIndex: Int): Boolean {
        val precondition = GameFlowActionContextPrecondition<T, A>(createOptionsContext(playerIndex))
        actionDsls().forEach { it.invoke(precondition) }
        return precondition.result
    }

    private fun createOptionsContext(playerIndex: Int)
        = ActionOptionsContext(gameData.game, actionType.name, playerIndex, gameData.eliminations, gameData.replayable)

    fun availableActions(playerIndex: Int, sampleSize: ActionSampleSize?): Iterable<A> {
        if (!checkPreconditions(playerIndex)) return emptyList()

        val context = GameFlowActionContextOptions(createOptionsContext(playerIndex), actionType, sampleSize)
        actionDsls().forEach { it.invoke(context) }
        return context.iterable()
    }

    fun actionAllowed(action: Actionable<T, A>): Boolean {
        val context = GameFlowActionContextRequires(createContext(action))
        val dsls = actionDsls()
        if (dsls.isEmpty()) {
            logger.warn { "No DSLs available in action allowed check: $action" }
            return false
        }
        dsls.forEach { it.invoke(context) }
        return context.result
    }

    fun actionInfoKeys(playerIndex: Int, previouslySelected: List<Any>): List<ActionInfoKey> {
        if (!checkPreconditions(playerIndex)) return emptyList()

        val context = GameFlowActionContextKeys(createOptionsContext(playerIndex), actionType, previouslySelected, this::actionAllowed)
        actionDsls().forEach { it.invoke(context) }
        return context.actionInfoKeys
    }

}
