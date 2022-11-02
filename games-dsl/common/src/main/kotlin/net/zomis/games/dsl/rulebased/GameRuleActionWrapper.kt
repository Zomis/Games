package net.zomis.games.dsl.rulebased

import net.zomis.games.dsl.*
import net.zomis.games.dsl.impl.GameRuleContext

class GameRuleActionWrapper<T: Any, A: Any>(
    private val gameContext: GameRuleContext<T>,
    private val action: GameActionRule<T, A>,
    private val parentRule: GameRuleImpl<T>
) : GameRuleActionScope<T, A> {

    private var appliesForActions: (ActionRuleScope<T, A>.() -> Boolean)? = null

    private fun appliesTo(action: ActionRuleScope<T, A>): Boolean {
        if (!parentRule.isActive(gameContext)) return false

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
            if (this@GameRuleActionWrapper.appliesTo(this)) rule.invoke(this)
        }
    }

    override fun perform(rule: ActionRuleScope<T, A>.() -> Unit) {
        action.effect {
            if (this@GameRuleActionWrapper.appliesTo(this)) rule.invoke(this)
        }
    }

    override fun precondition(rule: ActionOptionsScope<T>.() -> Boolean) {
        if (this.appliesForActions != null) {
            throw IllegalStateException("Unable to use precondition together with appliesForActions")
        }
        val outerThis = this
        action.precondition {
            if (outerThis.parentRule.isActive(this as GameRuleScope<T>)) rule.invoke(this) else true
        }
    }

    override fun requires(rule: ActionRuleScope<T, A>.() -> Boolean) {
        action.requires {
            if (this@GameRuleActionWrapper.appliesTo(this)) rule.invoke(this) else true
        }
    }

    override fun options(rule: ActionOptionsScope<T>.() -> Iterable<A>) {
        val outerThis = this
        action.options {
            if (outerThis.parentRule.isActive(this as GameRuleScope<T>)) rule.invoke(this) else emptyList()
        }
    }

    override fun choose(options: ActionChoicesScope<T, A>.() -> Unit) {
        val outerThis = this
        action.choose {
            if (outerThis.parentRule.isActive(this.context)) options.invoke(this)
        }
    }

}