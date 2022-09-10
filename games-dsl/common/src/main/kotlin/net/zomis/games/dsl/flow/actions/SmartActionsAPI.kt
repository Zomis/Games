package net.zomis.games.dsl.flow.actions

import net.zomis.games.dsl.ActionChoicesScope
import net.zomis.games.dsl.ActionOptionsScope
import net.zomis.games.dsl.ActionRuleScope
import net.zomis.games.dsl.flow.GameFlowActionContext
import net.zomis.games.dsl.flow.GameFlowActionDsl

interface SmartActionChoice<E>
interface SmartActionUsingScope<T: Any, A: Any> {
    fun <E> chosen(choice: SmartActionChoice<E>): E
    fun <E> chosenOptional(choice: SmartActionChoice<E>): E?
}


interface SmartActionUsingBuilder<T: Any, A: Any, E> {
    fun perform(function: ActionRuleScope<T, A>.(E) -> Unit): ActionEffect<T, A, E>
}

interface SmartActionScope<T: Any, A: Any> {
    fun <E> exampleChoices(name: String, optional: Boolean = false, function: () -> Iterable<E>): SmartActionChoice<E>
    fun <E> choice(name: String, optional: Boolean = false, function: () -> Iterable<E>): SmartActionChoice<E>
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
            handler._preconditions.add(ActionPrecondition())
        }
        override fun requires(rule: ActionRuleScope<T, A>.() -> Boolean) {
            handler._requires.add(ActionRequirement())
        }
        override fun choose(options: ActionChoicesScope<T, A>.() -> Unit) {
            handler._choices[""] = ActionChoice("", optional = false, exhaustive = true)
        }
        override fun options(rule: ActionOptionsScope<T>.() -> Iterable<A>) {
            handler._choices[""] = ActionChoice("", optional = false, exhaustive = true)
        }
        override fun perform(rule: ActionRuleScope<T, A>.() -> Unit) {
            handler._effect.add(ActionEffect())
        }
        override fun after(rule: ActionRuleScope<T, A>.() -> Unit) {
            handler._postEffect.add(ActionEffect())
        }
    }
    fun <T: Any, A: Any> handlerFromDsl(actionDsl: GameFlowActionDsl<T, A>): SmartActionBuilder<T, A> {
        val context = Context<T, A>()
        actionDsl.invoke(context)
        return context.handler
    }
}

