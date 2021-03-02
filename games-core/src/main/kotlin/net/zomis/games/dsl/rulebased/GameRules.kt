package net.zomis.games.dsl.rulebased

import net.zomis.games.PlayerEliminations
import net.zomis.games.common.GameEvents
import net.zomis.games.dsl.*
import net.zomis.games.dsl.impl.GameMarker

@GameMarker
interface GameRules<T : Any> {
    fun rule(name: String, rule: GameRule<T>.() -> Any?): GameRule<T>
}

@GameMarker
interface GameRuleScope<T : Any> {
    val game: T
    val eliminations: PlayerEliminations
    val replayable: ReplayableScope
}

interface GameRuleEventScope<T: Any, E>: GameRuleScope<T> {
    val event: E
}

@GameMarker
interface GameRuleForEach<T : Any, E> {
    fun effect(effect: GameRuleScope<T>.(E) -> Unit)
}

interface GameCommonRule<T: Any> {
    fun appliesWhen(condition: GameRuleScope<T>.() -> Boolean)
    fun effect(effect: GameRuleScope<T>.() -> Unit)
    fun <E> applyForEach(list: GameRuleScope<T>.() -> Iterable<E>): GameRuleForEach<T, E>
    fun <E> onEvent(gameEvents: GameRuleScope<T>.() -> GameEvents<E>): GameRuleEvents<T, E>
}

@GameMarker
interface GameRule<T : Any>: GameCommonRule<T> {
    fun gameSetup(effect: GameRuleScope<T>.() -> Unit)
    fun <A: Any> action(actionType: ActionType<T, A>, actionRule: GameRuleAction<T, A>.() -> Unit)

    fun rule(name: String, rule: GameRule<T>.() -> Any?): GameRule<T>
}

interface GameRuleEvents<T: Any, E> {
//    fun filter(...): GameRuleEvents<T, E>
    fun perform(perform: GameRuleEventScope<T, E>.() -> Unit)
}

interface GameRuleAction<T : Any, A: Any> {
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

