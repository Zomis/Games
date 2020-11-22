package net.zomis.games.dsl.flow

import net.zomis.games.PlayerEliminations
import net.zomis.games.dsl.*
import net.zomis.games.dsl.impl.*
import kotlin.reflect.KClass

class GameFlowActionContextAllowedCheck<T: Any, A: Any>(private val context: ActionRuleContext<T, A>): GameFlowActionContext<T, A>() {
    var result = true
    override fun precondition(rule: ActionOptionsScope<T>.() -> Boolean) {
        result = result && rule.invoke(context)
    }

    override fun requires(rule: ActionRuleScope<T, A>.() -> Boolean) {
        result = result && rule.invoke(context)
    }
}

class GameFlowActionContextPerform<T: Any, A: Any>: GameFlowActionContext<T, A>() {
    private val performs = mutableListOf<ActionRuleScope<T, A>.() -> Unit>()
    private val afters = mutableListOf<ActionRuleScope<T, A>.() -> Unit>()
    override fun perform(rule: ActionRuleScope<T, A>.() -> Unit) {
        performs.add(rule)
    }
    override fun after(rule: ActionRuleScope<T, A>.() -> Unit) {
        afters.add(rule)
    }
    fun execute(scope: ActionRuleScope<T, A>) {
        performs.forEach { it.invoke(scope) }
        afters.forEach { it.invoke(scope) }
    }
}

class GameFlowActionContextOptions<T: Any, A: Any>(private val context: ActionRuleContext<T, A>): GameFlowActionContext<T, A>() {
    // options and choose
}

open class GameFlowActionContext<T: Any, A: Any>: GameFlowActionScope<T, A> {
    override fun after(rule: ActionRuleScope<T, A>.() -> Unit) {}
    override fun perform(rule: ActionRuleScope<T, A>.() -> Unit) {}
    override fun precondition(rule: ActionOptionsScope<T>.() -> Boolean) {}
    override fun requires(rule: ActionRuleScope<T, A>.() -> Boolean) {}
    override fun options(rule: ActionOptionsScope<T>.() -> Iterable<A>) {}
    override fun choose(options: ActionChoicesStartScope<T, A>.() -> Unit) {}
}

private class GameFlowLogicAction<T: Any, A: Any>(
    private val game: T,
    override val actionType: ActionType<T, A>,
    private val actionDsl: GameFlowActionScope<T, A>.() -> Unit,
    private val feedback: (GameFlowContext.Steps.FlowStep) -> Unit,
    private val eliminations: PlayerEliminations,
    private val replayable: ReplayState
) : GameLogicActionType<T, A> {
    private fun createContext(action: Actionable<T, A>) = ActionRuleContext(game, action, eliminations, replayable)

    override fun availableActions(playerIndex: Int, sampleSize: ActionSampleSize?): Iterable<Actionable<T, A>> {
        TODO("Not yet implemented available")
    }

    override fun actionAllowed(action: Actionable<T, A>): Boolean {
        val context = GameFlowActionContextAllowedCheck(createContext(action))
        actionDsl.invoke(context)
        return context.result
    }

    override fun replayAction(action: Actionable<T, A>, state: Map<String, Any>?) {
        if (state != null) {
            replayable.setReplayState(state)
        }
        performAction(action)
    }

    override fun performAction(action: Actionable<T, A>) {
        if (!checkActionAllowed(action)) return
        val context = GameFlowActionContextPerform<T, A>()
        actionDsl.invoke(context)
        context.execute(createContext(action))
    }

    private fun checkActionAllowed(action: Actionable<T, A>): Boolean {
        if (!actionAllowed(action)) {
            feedback.invoke(GameFlowContext.Steps.IllegalAction(action.actionType, action.playerIndex, action.parameter))
            return false
        }
        return true
    }

    override fun createAction(playerIndex: Int, parameter: A): Actionable<T, A>
        = Action(game, playerIndex, actionType.name, parameter)

}

class GameFlowActionsImpl<T: Any>(
    private val feedback: (GameFlowContext.Steps.FlowStep) -> Unit,
    private val model: T,
    private val eliminations: PlayerEliminations,
    private val replayable: ReplayState
) : Actions<T> {

    private val actions = mutableListOf<ActionTypeImplEntry<T, Any>>()
    fun clear() { this.actions.clear() }

    fun <A: Any> add(actionType: ActionType<T, A>, actionDsl: GameFlowActionScope<T, A>.() -> Unit) {
        require(actions.none { it.name == actionType.name }) {
            "Actions must only be entered once within each same step. Unable to define action '${actionType.name}' again"
        }
        val entry = ActionTypeImplEntry(model, replayable, eliminations, actionType,
            GameFlowLogicAction(model, actionType, actionDsl, feedback, eliminations, replayable)
        )
        actions.add(entry as ActionTypeImplEntry<T, Any>)
    }

    override val actionTypes: Set<String> get() = actions.map { it.name }.toSet()
    override fun types(): Set<ActionTypeImplEntry<T, Any>> = actions.toSet()
    override fun get(actionType: String): ActionTypeImplEntry<T, Any>? = actions.find { it.name == actionType }
    override fun <A : Any> type(actionType: ActionType<T, A>): ActionTypeImplEntry<T, A>?
        = actions.find { it.actionType == actionType } as ActionTypeImplEntry<T, A>?

    override fun type(actionType: String): ActionTypeImplEntry<T, Any>? = actions.find { it.name == actionType }
    override fun <P : Any> type(actionType: String, clazz: KClass<P>): ActionTypeImplEntry<T, P>? {
        val entry = actions.find { it.name == actionType }
        return if (entry?.parameterClass == clazz) entry as ActionTypeImplEntry<T, P> else null
    }

}
