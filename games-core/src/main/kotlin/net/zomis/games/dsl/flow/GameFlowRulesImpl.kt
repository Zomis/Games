package net.zomis.games.dsl.flow

import net.zomis.games.common.GameEvents
import net.zomis.games.dsl.*
import net.zomis.games.dsl.flow.rules.GameRulePresets
import net.zomis.games.dsl.flow.rules.GameRulePresetsImpl
import net.zomis.games.dsl.impl.GameMarker
import net.zomis.games.dsl.impl.GameRuleContext
import net.zomis.games.dsl.rulebased.*

@GameMarker
interface GameFlowRule<T : Any>: GameCommonRule<T> {
    fun rule(name: String, rule: GameFlowRule<T>.() -> Any?)

    fun view(key: String, value: ViewScope<T>.() -> Any?)
    fun <A: Any> action(actionType: ActionType<T, A>, actionDsl: GameFlowActionDsl<T, A>)
//    suspend fun loop(function: suspend GameFlowScope<T>.() -> Unit)
//    suspend fun step(name: String, step: suspend GameFlowStepScope<T>.() -> Unit)
//    suspend fun log(logging: LogScope<T>.() -> String)
//    suspend fun logSecret(playerIndex: Int, logging: LogScope<T>.() -> String): LogSecretScope<T>
}

@GameMarker
interface GameFlowRules<T: Any> {
    val rules: GameRulePresets<T>
    fun rule(name: String, rule: GameFlowRule<T>.() -> Any?)
    fun afterActionRule(name: String, rule: GameFlowRule<T>.() -> Any?)
    fun beforeReturnRule(name: String, rule: GameFlowRule<T>.() -> Any?)
}

enum class GameFlowRulesState { AFTER_ACTIONS, BEFORE_RETURN, FIRE_EVENT }
class GameFlowRulesContext<T: Any>(
    val context: GameRuleContext<T>,
    val state: GameFlowRulesState,
    val event: Pair<GameEvents<*>, Any>?,
    val callbacks: GameFlowRuleCallbacks<T>
): GameFlowRules<T> {
    override val rules = GameRulePresetsImpl(this)
    override fun afterActionRule(name: String, rule: GameFlowRule<T>.() -> Any?) {
        if (state == GameFlowRulesState.AFTER_ACTIONS) {
            runRule(name, rule)
        }
    }

    override fun beforeReturnRule(name: String, rule: GameFlowRule<T>.() -> Any?) {
        if (state == GameFlowRulesState.BEFORE_RETURN) {
            runRule(name, rule)
        }
    }

    override fun rule(name: String, rule: GameFlowRule<T>.() -> Any?) {
        runRule(name, rule)
    }

    private fun runRule(name: String, rule: GameFlowRule<T>.() -> Any?) {
        GameFlowRuleContextRun(context, callbacks).runRule(name, rule)
    }

    fun <E> fire(executor: GameEvents<E>, event: E) {
        TODO("firing events not implemented yet in GameFlow")
    }

}

open class GameFlowRuleContext<T: Any>: GameFlowRule<T> {
    override fun appliesWhen(condition: GameRuleScope<T>.() -> Boolean) {}
    override fun effect(effect: GameRuleScope<T>.() -> Unit) {}
    override fun <E> applyForEach(list: GameRuleScope<T>.() -> Iterable<E>): GameRuleForEach<T, E> {
        return object : GameRuleForEach<T, E> {
            override fun effect(effect: GameRuleScope<T>.(E) -> Unit) {}
        }
    }
    override fun rule(name: String, rule: GameFlowRule<T>.() -> Any?) {}
    override fun <E> onEvent(gameEvents: GameRuleScope<T>.() -> GameEvents<E>): GameRuleEvents<T, E> {
        return object : GameRuleEvents<T, E> {
            override fun perform(perform: GameRuleEventScope<T, E>.() -> Unit) {}
        }
    }
    override fun view(key: String, value: ViewScope<T>.() -> Any?) {}
    override fun <A : Any> action(actionType: ActionType<T, A>, actionDsl: GameFlowActionDsl<T, A>) {}
}
interface GameFlowRuleCallbacks<T: Any> {
    fun view(key: String, value: ViewScope<T>.() -> Any?)
    val feedback: (GameFlowContext.Steps.FlowStep) -> Unit
    fun <A : Any> action(action: ActionType<T, A>, actionDsl: GameFlowActionScope<T, A>.() -> Unit)
}

class GameFlowRuleContextRun<T: Any>(
    private val context: GameRuleContext<T>,
    private val callbacks: GameFlowRuleCallbacks<T>
) {
    fun runRule(name: String, rule: GameFlowRule<T>.() -> Any?) {
        val activeCheck = GameFlowRuleContextActiveCheck(context)
        rule.invoke(activeCheck)
        if (!activeCheck.result) return

        callbacks.feedback.invoke(GameFlowContext.Steps.RuleExecution(name, Unit))
        val execution = GameFlowRuleContextExecution(context, callbacks)
        rule.invoke(execution)
    }
}

class GameFlowRuleContextActiveCheck<T: Any>(
    private val context: GameRuleContext<T>
) : GameFlowRuleContext<T>() {
    var result: Boolean = true

    override fun appliesWhen(condition: GameRuleScope<T>.() -> Boolean) {
        result = result && condition.invoke(context)
    }
}
class GameFlowRuleContextExecution<T: Any>(
    private val context: GameRuleContext<T>,
    private val callbacks: GameFlowRuleCallbacks<T>
): GameFlowRuleContext<T>() {

    class GameRuleForEachImpl<T: Any, E>(val context: GameRuleContext<T>, val list: Iterable<E>): GameRuleForEach<T, E> {
        override fun effect(effect: GameRuleScope<T>.(E) -> Unit) {
            list.forEach { effect.invoke(context, it) }
        }
    }
    override fun <E> applyForEach(list: GameRuleScope<T>.() -> Iterable<E>): GameRuleForEach<T, E> {
        return GameRuleForEachImpl(context, list.invoke(context))
    }

    override fun effect(effect: GameRuleScope<T>.() -> Unit) {
        effect.invoke(context)
    }

    override fun rule(name: String, rule: GameFlowRule<T>.() -> Any?) {
        GameFlowRuleContextRun(context, callbacks).runRule(name, rule)
    }

    override fun view(key: String, value: ViewScope<T>.() -> Any?) {
        callbacks.view(key, value)
    }

    override fun <A : Any> action(actionType: ActionType<T, A>, actionDsl: GameFlowActionDsl<T, A>) {
        callbacks.action(actionType, actionDsl)
    }

    override fun <E> onEvent(gameEvents: GameRuleScope<T>.() -> GameEvents<E>): GameRuleEvents<T, E> {
        TODO()
    }

}