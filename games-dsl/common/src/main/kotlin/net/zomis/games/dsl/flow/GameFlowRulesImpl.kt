package net.zomis.games.dsl.flow

import net.zomis.games.api.UsageScope
import net.zomis.games.dsl.*
import net.zomis.games.dsl.flow.rules.GameRulePresets
import net.zomis.games.dsl.flow.rules.GameRulePresetsImpl
import net.zomis.games.dsl.impl.FlowStep
import net.zomis.games.dsl.impl.GameMarker
import net.zomis.games.dsl.rulebased.*

@GameMarker
interface GameFlowRuleScope<T : Any>: GameCommonRule<T> {
    val game: T
    fun rule(name: String, rule: GameFlowRuleScope<T>.() -> Any?)

    fun view(key: String, value: ViewScope<T>.() -> Any?)
    fun viewModel(viewModel: ViewModel<T, *>)
    fun <A: Any> action(actionType: ActionType<T, A>, actionDsl: GameFlowActionDsl<T, A>)
}

@GameMarker
interface GameFlowRulesScope<T: Any> : UsageScope {
    val rules: GameRulePresets<T>
    fun rule(name: String, rule: GameFlowRuleScope<T>.() -> Any?)
    fun afterActionRule(name: String, rule: GameFlowRuleScope<T>.() -> Any?)
    fun beforeReturnRule(name: String, rule: GameFlowRuleScope<T>.() -> Any?)
    @Deprecated("apply rules instead, don't add/remove")
    fun <Owner> addRule(owner: Owner, rule: GameModifierScope<T, Owner>.() -> Unit)
}

enum class GameFlowRulesState { AFTER_ACTIONS, BEFORE_RETURN, FIRE_EVENT }
class GameFlowRulesContext<T: Any>(
    val context: GameMetaScope<T>,
    val state: GameFlowRulesState,
    val callbacks: GameFlowRuleCallbacks<T>
): GameFlowRulesScope<T> {
    override fun <Owner> addRule(owner: Owner, rule: GameModifierScope<T, Owner>.() -> Unit) = this.context.addRule(owner, rule)

    override val rules = GameRulePresetsImpl(this)
    override fun afterActionRule(name: String, rule: GameFlowRuleScope<T>.() -> Any?) {
        if (state == GameFlowRulesState.AFTER_ACTIONS) {
            runRule(name, rule)
        }
    }

    override fun beforeReturnRule(name: String, rule: GameFlowRuleScope<T>.() -> Any?) {
        if (state == GameFlowRulesState.BEFORE_RETURN) {
            runRule(name, rule)
        }
    }

    override fun rule(name: String, rule: GameFlowRuleScope<T>.() -> Any?) {
        runRule(name, rule)
    }

    private fun runRule(name: String, rule: GameFlowRuleScope<T>.() -> Any?) {
        GameFlowRuleContextRun(context, callbacks).runRule(name, rule)
    }

}

abstract class GameFlowRuleContext<T: Any>: GameFlowRuleScope<T> {
    override fun appliesWhen(condition: GameRuleScope<T>.() -> Boolean) {}
    override fun effect(effect: GameRuleScope<T>.() -> Unit) {}
    override fun <E> applyForEach(list: GameRuleScope<T>.() -> Iterable<E>): GameRuleForEachScope<T, E> {
        return object : GameRuleForEachScope<T, E> {
            override fun effect(effect: GameRuleScope<T>.(E) -> Unit) {}
        }
    }
    override fun rule(name: String, rule: GameFlowRuleScope<T>.() -> Any?) {}
    override fun view(key: String, value: ViewScope<T>.() -> Any?) {}
    override fun viewModel(viewModel: ViewModel<T, *>) {}
    override fun <A : Any> action(actionType: ActionType<T, A>, actionDsl: GameFlowActionDsl<T, A>) {}
}
interface GameFlowRuleCallbacks<T: Any> {
    fun view(key: String, value: ViewScope<T>.() -> Any?)
    fun viewModel(viewModel: ViewModel<T, *>)
    val feedback: (FlowStep) -> Unit
    fun <A : Any> action(action: ActionType<T, A>, actionDsl: GameFlowActionScope<T, A>.() -> Unit)
}

class GameFlowRuleContextRun<T: Any>(
    private val context: GameMetaScope<T>,
    private val callbacks: GameFlowRuleCallbacks<T>,
) {
    fun runRule(name: String, rule: GameFlowRuleScope<T>.() -> Any?) {
        val activeCheck = GameFlowRuleContextActiveCheck(context)
        rule.invoke(activeCheck)
        if (!activeCheck.result) return

        callbacks.feedback.invoke(FlowStep.RuleExecution(name, Unit))
        val execution = GameFlowRuleContextExecution(context, callbacks)
        rule.invoke(execution)
    }
}

class GameFlowRuleContextActiveCheck<T: Any>(
    private val context: GameMetaScope<T>
) : GameFlowRuleContext<T>() {
    override val game: T get() = context.game
    var result: Boolean = true

    override fun appliesWhen(condition: GameRuleScope<T>.() -> Boolean) {
        result = result && condition.invoke(context)
    }
}
class GameFlowRuleContextExecution<T: Any>(
    private val context: GameMetaScope<T>,
    private val callbacks: GameFlowRuleCallbacks<T>,
): GameFlowRuleContext<T>() {
    override val game: T get() = context.game

    class GameRuleForEachImpl<T: Any, E>(val context: GameMetaScope<T>, val list: Iterable<E>): GameRuleForEachScope<T, E> {
        override fun effect(effect: GameRuleScope<T>.(E) -> Unit) {
            list.forEach { effect.invoke(context, it) }
        }
    }
    override fun <E> applyForEach(list: GameRuleScope<T>.() -> Iterable<E>): GameRuleForEachScope<T, E> {
        return GameRuleForEachImpl(context, list.invoke(context))
    }

    override fun effect(effect: GameRuleScope<T>.() -> Unit) {
        effect.invoke(context)
    }

    override fun rule(name: String, rule: GameFlowRuleScope<T>.() -> Any?) {
        GameFlowRuleContextRun(context, callbacks).runRule(name, rule)
    }

    override fun view(key: String, value: ViewScope<T>.() -> Any?) {
        callbacks.view(key, value)
    }

    override fun viewModel(viewModel: ViewModel<T, *>) {
        callbacks.viewModel(viewModel)
    }

    override fun <A : Any> action(actionType: ActionType<T, A>, actionDsl: GameFlowActionDsl<T, A>) {
        callbacks.action(actionType, actionDsl)
    }

}