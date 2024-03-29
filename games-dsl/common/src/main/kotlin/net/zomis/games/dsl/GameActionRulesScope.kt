package net.zomis.games.dsl

import net.zomis.games.PlayerEliminationsRead
import net.zomis.games.PlayerEliminationsWrite
import net.zomis.games.api.MetaScope
import net.zomis.games.api.UsageScope
import net.zomis.games.common.PlayerIndex
import net.zomis.games.dsl.events.EventFactory
import net.zomis.games.dsl.flow.GameMetaScope
import net.zomis.games.dsl.impl.ActionOptionsContext
import net.zomis.games.dsl.impl.GameMarker
import kotlin.reflect.KClass

@GameMarker
interface GameActionRulesScope<T : Any>: UsageScope {
    val meta: GameMetaScope<T>
    val allActions: GameAllActionsRule<T>
    fun <A : Any> action(actionType: ActionType<T, A>): GameActionRule<T, A>
    fun <A : Any> action(actionType: ActionType<T, A>, ruleSpec: GameActionSpecificationScope<T, A>.() -> Unit)
    fun view(key: String, value: ViewScope<T>.() -> Any?)
    fun gameStart(onStart: GameStartScope<T>.() -> Unit)
    fun <E : Any> trigger(triggerClass: KClass<E>): EventFactory<E>
}

@GameMarker
interface GameStartScope<T : Any>: UsageScope {
    val game: T
    val replayable: ReplayStateI
    val playerCount: Int
    fun <E: Any> config(config: GameConfig<E>): E
}

interface LogSecretScope<T : Any>: UsageScope {
    fun publicLog(logging: LogScope<T>.() -> String)
}
interface LogSecretActionScope<T : Any, A : Any>: UsageScope {
    fun publicLog(logging: LogActionScope<T, A>.() -> String)
}
@GameMarker
interface LogScope<T : Any>: UsageScope {
    val game: T
    fun obj(value: Any): String
    fun player(value: PlayerIndex): String
    fun players(playerIndices: Iterable<Int>): String
    fun viewLink(text: String, type: String, view: Any): String
    fun inline(type: String, data: Any): String
}
@GameMarker
interface LogActionScope<T : Any, A : Any>: LogScope<T> {
    val player: String
    val action: A
}
@GameMarker
interface ActionRuleScope<T : Any, A : Any> : GameUtils, ActionOptionsScope<T>, EventTools, MetaScope<T>, UsageScope {
    override val meta: GameMetaScope<T>
    override val game: T
    val action: Actionable<T, A>
    override val eliminations: PlayerEliminationsWrite
    override val replayable: ReplayStateI
    fun log(logging: LogActionScope<T, A>.() -> String)
    fun logSecret(player: PlayerIndex, logging: LogActionScope<T, A>.() -> String): LogSecretActionScope<T, A>
}
@GameMarker
interface ActionOptionsScope<T : Any> : UsageScope {
    val game: T
    val actionType: String
    val playerIndex: Int
}

interface GameAllActionsRule<T : Any> {
    fun after(rule: ActionRuleScope<T, Any>.() -> Unit)
    fun precondition(rule: ActionOptionsScope<T>.() -> Boolean)
}

@GameMarker
@Deprecated("Too similar to GameFlowActionScope")
interface GameActionSpecificationScope<T : Any, A : Any>: UsageScope {
    fun after(rule: ActionRuleScope<T, A>.() -> Unit)
    fun effect(rule: ActionRuleScope<T, A>.() -> Unit)
    fun precondition(rule: ActionOptionsScope<T>.() -> Boolean)
    fun requires(rule: ActionRuleScope<T, A>.() -> Boolean)
    fun options(rule: ActionOptionsScope<T>.() -> Iterable<A>)
    fun forceWhen(rule: ActionOptionsScope<T>.() -> Boolean)
    fun choose(options: ActionChoicesScope<T, A>.() -> Unit)
}

interface GameActionRule<T : Any, A : Any> : GameActionSpecificationScope<T, A> {
    operator fun invoke(ruleSpec: GameActionSpecificationScope<T, A>.() -> Unit)
}

@GameMarker
interface ActionChoicesRecursiveScope<T : Any, C : Any> : UsageScope {
    val chosen: C
    val game: T
    val eliminations: PlayerEliminationsRead
    val actionType: String
    val playerIndex: Int
}

@GameMarker
interface ActionChoicesRecursiveSpecScope<T : Any, C: Any, P : Any> : UsageScope {
    val chosen: C
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

@GameMarker
interface ActionChoicesScope<T : Any, P : Any> : UsageScope {
    fun parameter(parameter: P)
    val context: ActionOptionsContext<T>
    fun <C : Any> recursive(base: C, options: ActionChoicesRecursiveSpecScope<T, C, P>.() -> Unit)
    fun <E : Any> options(options: ActionOptionsScope<T>.() -> Iterable<E>, next: ActionChoicesScope<T, P>.(E) -> Unit)
    fun <E : Any> optionsWithIds(options: ActionOptionsScope<T>.() -> Iterable<Pair<String, E>>, next: ActionChoicesScope<T, P>.(E) -> Unit)
}
