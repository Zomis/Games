package net.zomis.games.dsl.flow

import klog.KLoggers
import net.zomis.games.dsl.*
import net.zomis.games.dsl.flow.actions.ActionPrecondition
import net.zomis.games.dsl.flow.actions.SmartActionBuilder
import net.zomis.games.dsl.flow.actions.SmartActionLogic
import net.zomis.games.dsl.flow.actions.SmartActions
import net.zomis.games.dsl.impl.*
import kotlin.reflect.KClass

open class GameFlowActionContext<T: Any, A: Any>: GameFlowActionScope<T, A> {
    override fun after(rule: ActionRuleScope<T, A>.() -> Unit) {}
    override fun perform(rule: ActionRuleScope<T, A>.() -> Unit) {}
    override fun precondition(rule: ActionOptionsScope<T>.() -> Boolean) {}
    override fun requires(rule: ActionRuleScope<T, A>.() -> Boolean) {}
    override fun options(rule: ActionOptionsScope<T>.() -> Iterable<A>) = exampleOptions(rule)
    override fun exampleOptions(rule: ActionOptionsScope<T>.() -> Iterable<A>) {}
    override fun choose(options: ActionChoicesScope<T, A>.() -> Unit) {}
    override fun preconditionDenyIf(rule: ActionOptionsScope<T>.() -> Boolean) { TODO() }
}

typealias GameFlowActionDsl<T, A> = GameFlowActionScope<T, A>.() -> Unit
class GameFlowActionsImpl<T: Any>(
    private val feedback: (FlowStep) -> Unit,
    private val gameContext: GameMetaScope<T>,
) : Actions<T> {
    override val choices = ActionChoices()
    private val logger = KLoggers.logger(this)

    private val actions = mutableMapOf<String, ActionTypeImplEntry<T, Any>>()
    private val allActions = mutableListOf<ActionPrecondition<T, Unit>>()
    fun clear() {
        this.actions.clear()
        this.allActions.clear()
    }
    fun findAction(actionType: String): ActionTypeImplEntry<T, Any> = actions.getValue(actionType)

    fun <A: Any> add(actionType: ActionType<T, A>, handler: SmartActionBuilder<T, A>) {
        val entry = actions.getOrPut(actionType.name) {
            val smartAction = SmartActionLogic(gameContext, actionType, allActions)
            ActionTypeImplEntry(gameContext, actionType, smartAction) as ActionTypeImplEntry<T, Any>
        }
        (entry.impl as SmartActionLogic<T, A>).add(handler)
    }

    fun <A: Any> add(actionType: ActionType<T, A>, actionDsl: GameFlowActionDsl<T, A>)
        = add(actionType, SmartActions.handlerFromDsl(actionDsl))

    fun addGlobalPrecondition(rule: ActionOptionsScope<T>.() -> Boolean) {
        this.allActions.add(ActionPrecondition(rule))
    }

    override val actionTypes: Set<String> get() = actions.keys.toSet()
    override fun types(): Set<ActionTypeImplEntry<T, Any>> = actions.values.toSet()
    override fun get(actionType: String): ActionTypeImplEntry<T, Any>? = actions[actionType]
    override fun <A : Any> type(actionType: ActionType<T, A>): ActionTypeImplEntry<T, A>?
        = actions.values.find { it.actionType == actionType } as ActionTypeImplEntry<T, A>?

    override fun type(actionType: String): ActionTypeImplEntry<T, Any>? = get(actionType)
    override fun <P : Any> type(actionType: String, clazz: KClass<P>): ActionTypeImplEntry<T, P>? {
        val entry = actions.values.find { it.name == actionType }
        return if (entry?.parameterClass == clazz) entry as ActionTypeImplEntry<T, P> else null
    }

    fun clearAndPerform(action: Actionable<T, Any>, clearer: () -> Unit): ActionResult<T, out Any> {
        val existing = this.type(action.actionType)
        if (existing == null) {
            logger.warn { "No existing actionType definition found for $action" }
            return ActionResult(action, null).also { it.addPrecondition("actionType", null, false) }
        }
        val allowed = existing.checkAllowed(action)
        if (allowed.allowed) {
            clearer.invoke()
            allowed.addAll(existing.perform(action))
        }
        return allowed
    }

}
