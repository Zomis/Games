package net.zomis.games.dsl

import net.zomis.games.PlayerEliminations
import kotlin.reflect.KClass

interface GameRules<T : Any> {
    val allActions: GameAllActionsRule<T>
    fun <A : Any> action(actionType: ActionType<A>): GameActionRule<T, A>
    fun view(key: String, value: ViewScope<T>.() -> Any?)
    fun gameStart(onStart: GameStartScope<T>.() -> Unit)
    fun <E : Any> trigger(triggerClass: KClass<E>): GameRuleTrigger<T, E>
}

interface GameStartScope<T : Any> {
    val game: T
    val replayable: ReplayableScope
}
interface ActionRuleScope<T : Any, A : Any> : GameUtils, ActionOptionsScope<T> {
    override val game: T
    val action: Actionable<T, A>
    val eliminations: PlayerEliminations
    override val replayable: ReplayableScope
    override val playerEliminations: PlayerEliminations
        get() = eliminations
}
interface ActionOptionsScope<T : Any> {
    val game: T
    val actionType: String
    val playerIndex: Int
}

interface GameAllActionsRule<T : Any> {
    fun after(rule: ActionRuleScope<T, Any>.() -> Unit)
    fun precondition(rule: ActionOptionsScope<T>.() -> Boolean)
}

interface GameActionRule<T : Any, A : Any> {
    fun after(rule: ActionRuleScope<T, A>.() -> Unit)
    fun effect(rule: ActionRuleScope<T, A>.() -> Unit)
    fun precondition(rule: ActionOptionsScope<T>.() -> Boolean)
    fun requires(rule: ActionRuleScope<T, A>.() -> Boolean)
    fun options(rule: ActionOptionsScope<T>.() -> Iterable<A>)
    fun forceUntil(rule: ActionOptionsScope<T>.() -> Boolean)
    fun choose(options: ActionChoicesStartScope<T, A>.() -> Unit)
}

interface ActionChoicesNextScope<T : Any, A : Any> : ActionChoicesStartScope<T, A> {
    fun parameter(action: A)
}
interface ActionChoicesStartScope<T : Any, A : Any> {
    fun <E : Any> options(options: ActionOptionsScope<T>.() -> Iterable<E>, next: ActionChoicesNextScope<T, A>.(E) -> Unit)
}

interface GameRuleTriggerScope<T, E> {
    val game: T
    val trigger: E
    val replayable: ReplayableScope
    val eliminations: PlayerEliminations
}
interface GameRuleTrigger<T : Any, E : Any> {

    fun effect(effect: GameRuleTriggerScope<T, E>.() -> Unit): GameRuleTrigger<T, E>
    fun map(mapping: GameRuleTriggerScope<T, E>.() -> E): GameRuleTrigger<T, E>
    fun after(effect: GameRuleTriggerScope<T, E>.() -> Unit): GameRuleTrigger<T, E>
    fun ignoreEffectIf(condition: GameRuleTriggerScope<T, E>.() -> Boolean): GameRuleTrigger<T, E>
    operator fun invoke(trigger: E): E?

}