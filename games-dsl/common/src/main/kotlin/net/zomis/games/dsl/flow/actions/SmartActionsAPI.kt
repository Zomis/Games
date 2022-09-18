package net.zomis.games.dsl.flow.actions

import net.zomis.games.PlayerEliminationsRead
import net.zomis.games.common.putSingle
import net.zomis.games.dsl.ActionChoicesScope
import net.zomis.games.dsl.ActionOptionsScope
import net.zomis.games.dsl.ActionRuleScope
import net.zomis.games.dsl.Actionable
import net.zomis.games.dsl.flow.GameFlowActionContext
import net.zomis.games.dsl.flow.GameFlowActionDsl

interface SmartActionChoice<E>
interface SmartActionUsingScope<T: Any, A: Any> {
    val game: T
    val action: Actionable<T, A>
    val eliminations: PlayerEliminationsRead

//    fun <E> chosen(choice: SmartActionChoice<E>): E
//    fun <E> chosenOptional(choice: SmartActionChoice<E>): E?
}


interface SmartActionUsingBuilder<T: Any, A: Any, E> {
    fun precondition(rule: ActionOptionsScope<T>.() -> Boolean): ActionPrecondition<T, E>
    fun requires(rule: ActionRuleScope<T, A>.(E) -> Boolean): ActionRequirement<T, A, E>
    fun perform(function: ActionRuleScope<T, A>.(E) -> Unit): ActionEffect<T, A, E>
}

interface SmartActionScope<T: Any, A: Any> {
    fun exampleChoices(name: String, optional: Boolean, function: ActionOptionsScope<T>.() -> Iterable<A>): SmartActionChoice<A>
    fun choice(name: String, optional: Boolean, function: ActionOptionsScope<T>.() -> Iterable<A>): SmartActionChoice<A>
    fun <E> using(function: SmartActionUsingScope<T, A>.() -> E): SmartActionUsingBuilder<T, A, E>
    fun change(block: SmartActionChangeScope<T, A>.() -> Unit)
}

interface SmartActionChangeScope<T: Any, A: Any> {
    val handlers: Sequence<SmartActionBuilder<T, A>>
}

object SmartActions {
    private class Context<T: Any, A: Any>: GameFlowActionContext<T, A>() {
        val handler = SmartActionBuilder<T, A>()

        override fun precondition(rule: ActionOptionsScope<T>.() -> Boolean) {
            handler._preconditions.add(ActionPrecondition(rule))
        }
        override fun requires(rule: ActionRuleScope<T, A>.() -> Boolean) {
            handler._requires.add(ActionRequirement({}, { rule.invoke(this) }))
        }
        override fun choose(options: ActionChoicesScope<T, A>.() -> Unit) {
            handler._choices.putSingle("", ActionChoice("", optional = false, exhaustive = true, options))
        }
        override fun options(rule: ActionOptionsScope<T>.() -> Iterable<A>) {
            handler._choices.putSingle("", ActionChoice("", optional = false, exhaustive = true, handler.iterableToChoices(rule)))
        }
        override fun perform(rule: ActionRuleScope<T, A>.() -> Unit) {
            handler._effect.add(ActionEffect({  }, { rule.invoke(this) }))
        }
        override fun after(rule: ActionRuleScope<T, A>.() -> Unit) {
            handler._postEffect.add(ActionEffect({  }, { rule.invoke(this) }))
        }
    }
    fun <T: Any, A: Any> handlerFromDsl(actionDsl: GameFlowActionDsl<T, A>): SmartActionBuilder<T, A> {
        val context = Context<T, A>()
        actionDsl.invoke(context)
        return context.handler
    }
}

