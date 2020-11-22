package net.zomis.games.dsl.flow

import net.zomis.games.common.GameEvents
import net.zomis.games.dsl.flow.rules.GameRulePresets
import net.zomis.games.dsl.flow.rules.GameRulePresetsImpl
import net.zomis.games.dsl.impl.GameMarker
import net.zomis.games.dsl.impl.GameRuleContext
import net.zomis.games.dsl.rulebased.GameRuleEventScope
import net.zomis.games.dsl.rulebased.GameRuleEvents
import net.zomis.games.dsl.rulebased.GameRuleForEach
import net.zomis.games.dsl.rulebased.GameRuleScope

@GameMarker
interface GameFlowRule<T : Any> {
    fun appliesWhen(condition: GameRuleScope<T>.() -> Boolean)
    fun effect(effect: GameRuleScope<T>.() -> Unit)
    fun <E> applyForEach(list: GameRuleScope<T>.() -> Iterable<E>): GameRuleForEach<T, E>

    fun rule(name: String, rule: GameFlowRule<T>.() -> Any?)
    fun <E> onEvent(gameEvents: GameRuleScope<T>.() -> GameEvents<E>): GameRuleEvents<T, E>
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
    val feedback: (GameFlowContext.Steps.FlowStep) -> Unit
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
        val activeCheck = GameFlowRuleContextActiveCheck(context)
        rule.invoke(activeCheck)
        if (!activeCheck.result) return

        feedback.invoke(GameFlowContext.Steps.RuleExecution(name, Unit))
        val execution = GameFlowRuleContextExecution(context)
        rule.invoke(execution)
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
    override fun rule(name: String, rule: GameFlowRule<T>.() -> Any?) { TODO("nested rules not implemented yet") }
    override fun <E> onEvent(gameEvents: GameRuleScope<T>.() -> GameEvents<E>): GameRuleEvents<T, E> {
        return object : GameRuleEvents<T, E> {
            override fun perform(perform: GameRuleEventScope<T, E>.() -> Unit) {}
        }
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
    private val context: GameRuleContext<T>
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
        TODO()
    }

    override fun <E> onEvent(gameEvents: GameRuleScope<T>.() -> GameEvents<E>): GameRuleEvents<T, E> {
        TODO()
    }

}