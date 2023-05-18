package net.zomis.games.dsl

import net.zomis.games.api.*
import net.zomis.games.dsl.flow.GameFlowRulesScope
import net.zomis.games.dsl.flow.GameFlowScope
import net.zomis.games.dsl.impl.GameAI
import net.zomis.games.dsl.impl.GameAIScope
import net.zomis.games.dsl.impl.GameMarker
import net.zomis.games.scorers.ScorerFactory
import kotlin.reflect.KClass

data class ActionableOption(val actionType: String, val parameter: Any, val display: Any)
interface GameSerializable {
    fun serialize(): Any
}
interface EventTools : CompoundScope, MutableEliminationsScope, ReplayableScope, ConfigScope
interface GameUtils : CompoundScope, EliminationsScope, ReplayableScope, ConfigScope

class GameSpec<T : Any>(val name: String, val dsl: GameDslScope<T>.() -> Unit) {
    operator fun invoke(context: GameDslScope<T>) = dsl(context)
}
typealias GameTestDsl<T> = suspend GameTestScope<T>.() -> Unit
typealias GameModelDsl<T, C> = GameModelScope<T, C>.() -> Unit
typealias GameActionRulesDsl<T> = GameActionRulesScope<T>.() -> Unit
typealias GameFlowRulesDsl<T> = GameFlowRulesScope<T>.() -> Unit
typealias GameFlowDsl<T> = suspend GameFlowScope<T>.() -> Unit

interface GameConfig<E: Any> {
    fun mutable(): GameConfig<E>
    fun withDefaults(): GameConfig<E>

    val key: String
    val clazz: KClass<E>
    val default: () -> E
    var value: E
}
class GameConfigs(val configs: List<GameConfig<Any>>) {
    override fun toString(): String = "Configs($configs)"
    fun <E: Any> get(config: GameConfig<E>): E = configs.first { it.key == config.key }.value as E
    fun set(key: String, value: Any): GameConfigs {
        this.configs.first { it.key == key }.value = value
        return this
    }

    fun toJSON(): Any? {
        if (configs.isEmpty()) return null
        return if (isOldStyle()) {
            configs.single().value
        } else {
            configs.associate { it.key to it.value }
        }
    }

    fun isOldStyle(): Boolean {
        if (configs.isEmpty()) return true
        return configs.size <= 1 && configs.first().key == ""
    }
    fun isNotDefault(): Boolean = configs.any { it.value != it.default.invoke() }
    fun oldStyleValue(): Any {
        check(isOldStyle())
        return if (configs.isEmpty()) Unit else configs.single().value
    }
}

@GameMarker
interface GameDslScope<T : Any> : UsageScope {
    var useRandomAI: Boolean
    @Deprecated("use GameConfig class")
    fun <C : Any> setup(configClass: KClass<C>, modelDsl: GameModelDsl<T, C>)
    fun setup(modelDsl: GameModelDsl<T, Unit>)
    fun testCase(players: Int, testDsl: GameTestDsl<T>)
    fun actionRules(actionRulesDsl: GameActionRulesDsl<T>)
    fun gameFlow(flowDsl: GameFlowDsl<T>)
    fun gameFlowRules(flowRulesDsl: GameFlowRulesDsl<T>)
    fun <E: Any> config(key: String, default: () -> E): GameConfig<E>
    fun ai(name: String, block: GameAIScope<T>.() -> Unit): GameAI<T>
    val scorers: ScorerFactory<T>
}

class ViewModel<T, VM>(val factory: (T, Int) -> VM)

class GameCreator<T : Any>(val modelClass: KClass<T>) {
    fun game(name: String, dsl: GameDslScope<T>.() -> Unit): GameSpec<T> = GameSpec(name, dsl)
    fun <A: Any> action(name: String, parameterType: KClass<A>): GameActionCreator<T, A>
        = GameActionCreator(name, parameterType, parameterType, {it}, {it as A})
    fun singleAction(name: String) = this.action(name, Unit::class)
    fun <VM> viewModel(viewModelCreator: (model: T, playerIndex: Int) -> VM): ViewModel<T, VM> = ViewModel(viewModelCreator)
}
