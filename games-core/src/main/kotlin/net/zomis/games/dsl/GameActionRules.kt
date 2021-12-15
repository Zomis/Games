package net.zomis.games.dsl

import net.zomis.games.PlayerEliminationsRead
import net.zomis.games.PlayerEliminationsWrite
import net.zomis.games.common.PlayerIndex
import net.zomis.games.dsl.impl.ActionOptionsContext
import kotlin.reflect.KClass

interface GameActionRules<T : Any> {
    val allActions: GameAllActionsRule<T>
    fun <A : Any> action(actionType: ActionType<T, A>): GameActionRule<T, A>
    fun <A : Any> action(actionType: ActionType<T, A>, ruleSpec: GameActionSpecificationScope<T, A>.() -> Unit)
    fun view(key: String, value: ViewScope<T>.() -> Any?)
    fun gameStart(onStart: GameStartScope<T>.() -> Unit)
    fun <E : Any> trigger(triggerClass: KClass<E>): GameRuleTrigger<T, E>
}

interface GameStartScope<T : Any> {
    val game: T
    val replayable: ReplayableScope
}

interface LogSecretScope<T : Any> {
    fun publicLog(logging: LogScope<T>.() -> String)
}
interface LogSecretActionScope<T : Any, A : Any> {
    fun publicLog(logging: LogActionScope<T, A>.() -> String)
}
interface LogScope<T : Any> {
    val game: T
    fun obj(value: Any): String
    fun player(value: PlayerIndex): String
    fun players(playerIndices: Iterable<Int>): String
    fun viewLink(text: String, type: String, view: Any): String
    fun inline(type: String, data: Any): String
}
interface LogActionScope<T : Any, A : Any>: LogScope<T> {
    val player: String
    val action: A
}
interface ActionRuleScope<T : Any, A : Any> : GameUtils, ActionOptionsScope<T> {
    override val game: T
    val action: Actionable<T, A>
    val eliminations: PlayerEliminationsWrite
    override val replayable: ReplayableScope
    override val playerEliminations: PlayerEliminationsWrite
        get() = eliminations
    fun log(logging: LogActionScope<T, A>.() -> String)
    fun logSecret(player: PlayerIndex, logging: LogActionScope<T, A>.() -> String): LogSecretActionScope<T, A>
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

interface GameActionSpecificationScope<T : Any, A : Any> {
    fun after(rule: ActionRuleScope<T, A>.() -> Unit)
    fun effect(rule: ActionRuleScope<T, A>.() -> Unit)
    fun precondition(rule: ActionOptionsScope<T>.() -> Boolean)
    fun requires(rule: ActionRuleScope<T, A>.() -> Boolean)
    fun options(rule: ActionOptionsScope<T>.() -> Iterable<A>)
    fun forceWhen(rule: ActionOptionsScope<T>.() -> Boolean)
    @Deprecated("prefer forceWhen")
    fun forceUntil(rule: ActionOptionsScope<T>.() -> Boolean)
    fun choose(options: ActionChoicesScope<T, A>.() -> Unit)
}

interface GameActionRule<T : Any, A : Any> : GameActionSpecificationScope<T, A> {
    operator fun invoke(ruleSpec: GameActionSpecificationScope<T, A>.() -> Unit)
}

interface ActionChoicesRecursiveScope<T : Any, C : Any> {
    val chosen: C
    val game: T
    val eliminations: PlayerEliminationsRead
    val actionType: String
    val playerIndex: Int
}

interface ActionChoicesRecursiveSpecScope<T : Any, C: Any, P : Any> {
    val game: T
    val playerIndex: Int

    fun until(condition: ActionChoicesRecursiveScope<T, C>.() -> Boolean)
    fun <E : Any> options(options: ActionChoicesRecursiveScope<T, C>.() -> Iterable<E>, next: ActionChoicesRecursiveSpecScope<T, C, P>.(E) -> Unit)
    fun <E : Any> optionsWithIds(options: ActionChoicesRecursiveScope<T, C>.() -> Iterable<Pair<String, E>>, next: ActionChoicesRecursiveSpecScope<T, C, P>.(E) -> Unit)
    fun <E : Any> recursion(chosen: E, operation: (C, E) -> C)
    fun parameter(parameterCreator: ActionChoicesRecursiveScope<T, C>.() -> P)
    fun intermediateParameter(allowed: ActionChoicesRecursiveScope<T, C>.() -> Boolean)
    fun then(next: ActionChoicesScope<T, P>.() -> Unit)
}

interface ActionChoicesScope<T : Any, P : Any> {
    fun parameter(parameter: P)
    val context: ActionOptionsContext<T>
    fun <C : Any> recursive(base: C, options: ActionChoicesRecursiveSpecScope<T, C, P>.() -> Unit)
    fun <E : Any> options(options: ActionOptionsScope<T>.() -> Iterable<E>, next: ActionChoicesScope<T, P>.(E) -> Unit)
    fun <E : Any> optionsWithIds(options: ActionOptionsScope<T>.() -> Iterable<Pair<String, E>>, next: ActionChoicesScope<T, P>.(E) -> Unit)
}

interface GameRuleTriggerScope<T, E> {
    val game: T
    val trigger: E
    val replayable: ReplayableScope
    val eliminations: PlayerEliminationsWrite
}
interface GameRuleTrigger<T : Any, E : Any> {

    fun effect(effect: GameRuleTriggerScope<T, E>.() -> Unit): GameRuleTrigger<T, E>
    fun map(mapping: GameRuleTriggerScope<T, E>.() -> E): GameRuleTrigger<T, E>
    fun after(effect: GameRuleTriggerScope<T, E>.() -> Unit): GameRuleTrigger<T, E>
    fun ignoreEffectIf(condition: GameRuleTriggerScope<T, E>.() -> Boolean): GameRuleTrigger<T, E>
    operator fun invoke(trigger: E): E?

}
