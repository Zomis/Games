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
interface ActionRuleScope<T : Any, A : Any> {
    val game: T
    val action: Actionable<T, A>
    val eliminations: PlayerEliminations
    val replayable: ReplayableScope
}
interface ActionOptionsScope<T : Any> {
    val game: T
    val playerIndex: Int
}

interface GameAllActionsRule<T : Any> {
    fun after(rule: ActionRuleScope<T, Any>.() -> Unit)
    fun requires(rule: ActionRuleScope<T, Any>.() -> Boolean)
}

interface GameActionRule<T : Any, A : Any> {
    fun after(rule: ActionRuleScope<T, A>.() -> Unit)
    fun effect(rule: ActionRuleScope<T, A>.() -> Unit)
    fun requires(rule: ActionRuleScope<T, A>.() -> Boolean)
    fun options(rule: ActionOptionsScope<T>.() -> Iterable<A>)
    fun forceUntil(rule: ActionRuleScope<T, A>.() -> Boolean)
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
