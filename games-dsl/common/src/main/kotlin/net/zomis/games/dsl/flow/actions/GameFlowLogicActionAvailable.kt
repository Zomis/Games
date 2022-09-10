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
private class GameFlowActionContextOptions<T: Any, A: Any>(): GameFlowActionContext<T, A>() {
    var choicesRule: (ActionChoicesScope<T, A>.() -> Unit)? = null
    var optionsRule: (ActionOptionsScope<T>.() -> Iterable<A>)? = null

    private fun initialize() {
        check(this.optionsRule == null) { "options and/or choices can only be defined once" }
        check(this.choicesRule == null) { "options and/or choices can only be defined once" }
    }
    override fun options(rule: ActionOptionsScope<T>.() -> Iterable<A>) {
        initialize()
        this.optionsRule = rule
    }

    override fun choose(options: ActionChoicesScope<T, A>.() -> Unit) {
        initialize()
        this.choicesRule = options
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
        if (actionType.serializedType == Unit::class) {
            return listOf(Unit as A)
        }

        val context = GameFlowActionContextOptions<T, A>()
        actionDsls.invoke().forEach { it.invoke(context) }
        return when {
            context.optionsRule != null -> context.optionsRule!!.invoke(createOptionsContext(playerIndex))
            context.choicesRule != null -> {
                ActionComplexImpl(actionType, createOptionsContext(playerIndex), context.choicesRule!!).start()
                        .depthFirstActions(sampleSize).map { it.parameter }.asIterable()
            }
            else -> {
                logger.warn { "Action '${actionType.name}' in game with model ${gameData.game} has neither optionsRule or choicesRule set" }
                emptyList()
            }
        }
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

    @Deprecated("to be removed")
    fun actionInfoKeys(playerIndex: Int, previouslySelected: List<Any>): List<ActionInfoKey> {
        if (!checkPreconditions(playerIndex)) return emptyList()

        val context = GameFlowActionContextOptions<T, A>()
        actionDsls().forEach { it.invoke(context) }
        return when {
            context.optionsRule != null -> {
                val optionsContext = createOptionsContext(playerIndex)
                context.optionsRule!!.invoke(optionsContext)
                        .map { optionsContext.createAction(it) }
                        .filter { actionAllowed(it) }
                        .map { ActionInfoKey(actionType.serialize(it.parameter), actionType.name, emptyList(), true) }
            }
            context.choicesRule != null -> {
                ActionComplexImpl(actionType, createOptionsContext(playerIndex), context.choicesRule!!)
                        .withChosen(previouslySelected).actionKeys()
            }
            else -> {
                logger.warn { "Action '${actionType.name}' has neither optionsRule or choicesRule set" }
                emptyList()
            }
        }
    }

    fun withChosen(playerIndex: Int, chosen: List<Any>): ActionComplexChosenStep<T, A> {
        if (!checkPreconditions(playerIndex)) {
            return ActionComplexChosenStepEmpty(actionType, playerIndex, chosen)
        }

        val context = GameFlowActionContextOptions<T, A>()
        actionDsls().forEach { it.invoke(context) }
        if (context.optionsRule != null) {
            throw IllegalStateException("Cannot use withChosen on non-complex actionType ${actionType.name}")
        }
        return ActionComplexImpl(actionType, createOptionsContext(playerIndex), context.choicesRule!!).withChosen(chosen)
    }

    fun isComplex(): Boolean {
        val context = GameFlowActionContextOptions<T, A>()
        actionDsls().forEach { it.invoke(context) }
        return context.choicesRule != null
    }

}
