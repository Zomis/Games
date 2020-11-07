package net.zomis.games.dsl.rulebased

import net.zomis.games.dsl.ActionType
import net.zomis.games.dsl.impl.GameActionRulesContext
import net.zomis.games.dsl.impl.GameRuleContext

class GameRulesActive<T: Any>(val rules: List<GameRuleImpl<T>>) {

    fun runGameStart(context: GameRuleScope<T>): List<GameRule<T>> {
        val applied = mutableListOf<GameRuleImpl<T>>()
        rules.forEach {rule ->
            if (rule.gameSetup.isNotEmpty()) {
                rule.gameSetup.forEach { it.invoke(context) }
                applied.add(rule)
            }
        }
        return applied
    }

    fun stateCheck(context: GameRuleContext<T>): Collection<GameRule<T>> {
        val applied = mutableListOf<GameRuleImpl<T>>()
        rules.forEach {rule ->
            if (rule.effects.isNotEmpty()) {
                rule.effects.forEach { it.invoke(context) }
                applied.add(rule)
            }
            if (rule.forEachApplies.isNotEmpty()) {
                rule.forEachApplies.forEach {
                    if (it.performForEach(context)) {
                        applied.add(rule)
                    }
                }
            }
        }
        return applied
    }

}

class GameRuleForEachContext<T: Any, E>(val list: GameRuleScope<T>.() -> Iterable<E>): GameRuleForEach<T, E> {
    private val effects = mutableListOf<GameRuleScope<T>.(E) -> Unit>()

    override fun effect(effect: GameRuleScope<T>.(E) -> Unit) {
        effects.add(effect)
    }

    fun performForEach(context: GameRuleContext<T>): Boolean {
        var performedSomething = false
        list(context).forEach { listItem ->
            effects.forEach { effect ->
                effect.invoke(context, listItem)
                performedSomething = true
            }
        }
        return performedSomething
    }

}

class GameRuleImpl<T: Any>(
    val actionRulesContext: GameActionRulesContext<T>,
    val parentRule: GameRuleImpl<T>?,
    val name: String
): GameRule<T> {
    fun fullName(): String {
        return if (parentRule == null) name else "${parentRule.fullName()} - $name"
    }

    private val subRules = mutableListOf<GameRuleImpl<T>>()
    private var appliesWhen: GameRuleScope<T>.() -> Boolean = { true }
    internal var gameSetup: MutableList<GameRuleScope<T>.() -> Unit> = mutableListOf()
    internal var effects: MutableList<GameRuleScope<T>.() -> Unit> = mutableListOf()
    internal var forEachApplies: MutableList<GameRuleForEachContext<T, *>> = mutableListOf()

    override fun appliesWhen(condition: GameRuleScope<T>.() -> Boolean) {
        val oldApplies = appliesWhen
        appliesWhen = { oldApplies(this) && condition(this) }
    }

    override fun effect(effect: GameRuleScope<T>.() -> Unit) {
        this.effects.add(effect)
    }

    override fun <E> applyForEach(list: GameRuleScope<T>.() -> Iterable<E>): GameRuleForEach<T, E> {
        val forEachApplier = GameRuleForEachContext<T, E>(list)
        this.forEachApplies.add(forEachApplier)
        return forEachApplier
    }

    override fun gameSetup(effect: GameRuleScope<T>.() -> Unit) {
        this.gameSetup.add(effect)
    }

    override fun <A : Any> action(actionType: ActionType<T, A>, actionRule: GameRuleAction<T, A>.() -> Unit) {
        val ruleActionWrapper = GameRuleActionWrapper(actionRulesContext.action(actionType), this)
        actionRule.invoke(ruleActionWrapper)
    }

    override fun rule(name: String, rule: GameRule<T>.() -> Any?): GameRule<T> {
        val ruleImpl = GameRuleImpl(actionRulesContext, this, name)
        rule.invoke(ruleImpl)
        subRules.add(ruleImpl)
        return ruleImpl
    }

    fun subRules(): List<GameRuleImpl<T>> = subRules
    fun isActive(context: GameRuleScope<T>): Boolean {
        val parentAllowed = this.parentRule?.isActive(context) ?: true
        val thisAllowed = this.appliesWhen(context)
        return parentAllowed && thisAllowed
    }

}
