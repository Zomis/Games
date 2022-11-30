package net.zomis.games.dsl.rulebased

import net.zomis.games.api.*
import net.zomis.games.common.GameEvents
import net.zomis.games.dsl.*
import net.zomis.games.dsl.impl.GameMarker

@GameMarker
interface GameRulesScope<T : Any> : UsageScope {
    fun rule(name: String, rule: GameRuleRuleScope<T>.() -> Any?): GameRuleRuleScope<T>
}

@GameMarker
interface GameRuleScope<T : Any> : CompoundScope, net.zomis.games.api.GameModelScope<T>, MutableEliminationsScope,
    ReplayableScope, ConfigScope {
    val game: T get() = model
}

@Deprecated("old-style events handling. Use Event class instead")
interface GameRuleEventScope<T: Any, E>: GameRuleScope<T> {
    val event: E
}

@GameMarker
interface GameRuleForEachScope<T : Any, E> : UsageScope {
    fun effect(effect: GameRuleScope<T>.(E) -> Unit)
}

interface GameCommonRule<T: Any> : UsageScope {
    fun appliesWhen(condition: GameRuleScope<T>.() -> Boolean)
    fun effect(effect: GameRuleScope<T>.() -> Unit)
    fun <E> applyForEach(list: GameRuleScope<T>.() -> Iterable<E>): GameRuleForEachScope<T, E>
    fun <E> onEvent(gameEvents: GameRuleScope<T>.() -> GameEvents<E>): GameRuleEvents<T, E>
}

@GameMarker
interface GameRuleRuleScope<T : Any>: GameCommonRule<T> {
    fun gameSetup(effect: GameRuleScope<T>.() -> Unit)
    fun <A: Any> action(actionType: ActionType<T, A>, actionRule: GameRuleActionScope<T, A>.() -> Unit)

    fun rule(name: String, rule: GameRuleRuleScope<T>.() -> Any?): GameRuleRuleScope<T>
}

@Deprecated("old-style events handling. Use Event class instead")
interface GameRuleEvents<T: Any, E> {
//    fun filter(...): GameRuleEvents<T, E>
    fun perform(perform: GameRuleEventScope<T, E>.() -> Unit)
}

@GameMarker
interface GameRuleActionScope<T : Any, A: Any> : UsageScope {
    fun appliesForActions(condition: ActionRuleScope<T, A>.() -> Boolean)
    fun after(rule: ActionRuleScope<T, A>.() -> Unit)
    fun perform(rule: ActionRuleScope<T, A>.() -> Unit)
    fun precondition(rule: ActionOptionsScope<T>.() -> Boolean)
    fun requires(rule: ActionRuleScope<T, A>.() -> Boolean)
    fun options(rule: ActionOptionsScope<T>.() -> Iterable<A>)
    fun choose(options: ActionChoicesScope<T, A>.() -> Unit)
    // TODO: Can `forceWhen` be solved differently? By disabling other rules for example?
    // fun forceWhen(rule: ActionOptionsScope<T>.() -> Boolean)
}

