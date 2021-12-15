package net.zomis.games.dsl.flow

import klog.KLoggers
import net.zomis.games.PlayerEliminationsWrite
import net.zomis.games.dsl.*
import net.zomis.games.dsl.flow.actions.GameFlowLogicActionDelegator
import net.zomis.games.dsl.impl.*
import kotlin.reflect.KClass

open class GameFlowActionContext<T: Any, A: Any>: GameFlowActionScope<T, A> {
    override fun after(rule: ActionRuleScope<T, A>.() -> Unit) {}
    override fun perform(rule: ActionRuleScope<T, A>.() -> Unit) {}
    override fun precondition(rule: ActionOptionsScope<T>.() -> Boolean) {}
    override fun requires(rule: ActionRuleScope<T, A>.() -> Boolean) {}
    override fun options(rule: ActionOptionsScope<T>.() -> Iterable<A>) {}
    override fun choose(options: ActionChoicesScope<T, A>.() -> Unit) {}
}

typealias GameFlowActionDsl<T, A> = GameFlowActionScope<T, A>.() -> Unit
class GameFlowActionsImpl<T: Any>(
    private val feedback: (GameFlowContext.Steps.FlowStep) -> Unit,
    private val model: T,
    private val eliminations: PlayerEliminationsWrite,
    private val replayable: ReplayState
) : Actions<T> {
    override val choices = ActionChoices()
    private val logger = KLoggers.logger(this)

    private val actions = mutableListOf<ActionTypeImplEntry<T, Any>>()
    private val actionDsls = mutableMapOf<String, MutableList<GameFlowActionDsl<T, Any>>>()
    fun clear() {
        this.actions.clear()
        this.actionDsls.clear()
    }

    fun <A: Any> add(actionType: ActionType<T, A>, actionDsl: GameFlowActionDsl<T, A>) {
        if (actionDsls.any { it.key == actionType.name && it.value.contains(actionDsl as GameFlowActionDsl<T, Any>) }) {
            logger.info { "Ignoring duplicate DSL for action ${actionType.name}" }
            return
        }
        val gameRuleContext = GameRuleContext(model, eliminations, replayable)
        val entry = ActionTypeImplEntry(model, replayable, eliminations, actionType,
            GameFlowLogicActionDelegator(gameRuleContext, actionType, feedback) {
                actionDsls[actionType.name] as List<GameFlowActionDsl<T, A>>? ?: emptyList()
            }
        )
        if (!actions.any { it.actionType == actionType }) {
            actions.add(entry as ActionTypeImplEntry<T, Any>)
        }
        actionDsls.getOrPut(actionType.name) { mutableListOf() }.add(actionDsl as GameFlowActionDsl<T, Any>)
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

    fun clearAndPerform(action: Actionable<T, Any>, clearer: () -> Unit) {
        val gameRuleContext = GameRuleContext(model, eliminations, replayable)
        val existing = this.actions.find { it.name == action.actionType }
        if (existing == null) {
            logger.warn { "No existing actionType definition found for $action" }
            return
        }
        // Save DSLs so that they don't get reset before performing action
        val dsls = actionDsls[action.actionType] ?: emptyList<GameFlowActionDsl<T, Any>>()
        val delegator = GameFlowLogicActionDelegator(gameRuleContext, existing.actionType, feedback) { dsls }
        if (delegator.actionAllowed(action)) {
            clearer.invoke()
        } else {
            logger.error { "Action not allowed: $action" }
        }
        delegator.performAction(action)
    }

}
