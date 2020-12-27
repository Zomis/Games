package net.zomis.games.dsl.rulebased

import net.zomis.games.dsl.ActionChoicesStartScope
import net.zomis.games.dsl.ActionOptionsScope
import net.zomis.games.dsl.ActionRuleScope
import net.zomis.games.dsl.GameActionRule
import net.zomis.games.dsl.impl.ActionOptionsContext
import net.zomis.games.dsl.impl.ActionRuleContext
import net.zomis.games.dsl.impl.GameRuleContext
import net.zomis.games.dsl.impl.ReplayState

class GameRuleActionWrapper<T: Any, A: Any>(
    private val action: GameActionRule<T, A>,
    private val parentRule: GameRuleImpl<T>
) : GameRuleAction<T, A> {

    private var appliesForActions: (ActionRuleScope<T, A>.() -> Boolean)? = null

    private fun appliesTo(action: ActionRuleScope<T, A>): Boolean {
        val ruleScope = GameRuleContext(action.game, action.eliminations, action.replayable as ReplayState)
        if (!parentRule.isActive(ruleScope)) return false

        if (this.appliesForActions != null) {
            return this.appliesForActions!!.invoke(action)
        }
        return true
    }

    override fun appliesForActions(condition: ActionRuleScope<T, A>.() -> Boolean) {
        require(this.appliesForActions == null) { "Cannot set appliesForActions twice" }
        this.appliesForActions = condition
    }

    override fun after(rule: ActionRuleScope<T, A>.() -> Unit) {
        action.after {
            if (appliesTo(this)) rule.invoke(this)
        }
    }

    override fun perform(rule: ActionRuleScope<T, A>.() -> Unit) {
        action.effect {
            if (appliesTo(this)) rule.invoke(this)
        }
    }

    override fun precondition(rule: ActionOptionsScope<T>.() -> Boolean) {
        if (this.appliesForActions != null) {
            throw IllegalStateException("Unable to use precondition together with appliesForActions")
        }
        action.precondition {
            if (parentRule.isActive(this as GameRuleScope<T>)) rule.invoke(this) else true
        }
    }

    override fun requires(rule: ActionRuleScope<T, A>.() -> Boolean) {
        action.requires {
            if (appliesTo(this)) rule.invoke(this) else true
        }
    }

    override fun options(rule: ActionOptionsScope<T>.() -> Iterable<A>) {
        action.options {
            if (parentRule.isActive(this as GameRuleScope<T>)) rule.invoke(this) else emptyList()
        }
    }

    override fun choose(options: ActionChoicesStartScope<T, A>.() -> Unit) {
        action.choose {
            if (parentRule.isActive(this.context)) options.invoke(this)
        }
    }

}