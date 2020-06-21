package net.zomis.games.dsl

import net.zomis.games.PlayerEliminations

typealias ActionLogic<T, P, A> = ActionScope<T, P, A>.() -> Unit
typealias ActionLogicAdvanced<T, A> = ActionComplexScope<T, A>.() -> Unit

interface Replayable {
    fun toStateString(): String
}
interface ReplayableScope {
    fun map(key: String, default: () -> Map<String, Any>): Map<String, Any>
    fun int(key: String, default: () -> Int): Int
    fun ints(key: String, default: () -> List<Int>): List<Int>
    fun string(key: String, default: () -> String): String
    fun strings(key: String, default: () -> List<String>): List<String>
    fun list(key: String, default: () -> List<Map<String, Any>>): List<Map<String, Any>>
}
@Deprecated("Use ReplayableScope instead")
interface ReplayScope {
    fun state(key: String): Any
    fun fullState(key: String): Any?
}
interface EffectScope : GameUtils {
    override val playerEliminations: PlayerEliminations
    override val replayable: ReplayableScope
    fun state(key: String, value: Any)
}

interface ActionScope<T : Any, P : Any, A : Actionable<T, P>> {

    fun allowed(condition: (A) -> Boolean)
    fun effect(effect: EffectScope.(A) -> Unit)
    fun replayEffect(effect: ReplayScope.(A) -> Unit)

}

interface ActionComplexScopeResultNext<T : Any, A : Any> : ActionComplexScopeResultStart<T, A> {
    fun actionParameter(action: A)
}
interface ActionComplexScopeResultStart<T : Any, A : Any> {
    fun <E : Any> optionFrom(options: (T) -> Iterable<E>, next: ActionComplexScopeResultNext<T, A>.(E) -> Unit)
    fun <E : Any> option(options: Iterable<E>, next: ActionComplexScopeResultNext<T, A>.(E) -> Unit)
}

interface ActionComplexScope<T : Any, A : Any> {

    fun options(options: ActionComplexScopeResultStart<T, A>.() -> Unit)
    fun allowed(condition: (Action<T, A>) -> Boolean)
    fun effect(effect: EffectScope.(Action<T, A>) -> Unit)
    fun replayEffect(effect: ReplayScope.(Action<T, A>) -> Unit)

}