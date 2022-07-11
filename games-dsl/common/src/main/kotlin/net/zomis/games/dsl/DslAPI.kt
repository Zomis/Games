package net.zomis.games.dsl

import net.zomis.games.PlayerEliminationsRead
import net.zomis.games.PlayerEliminationsWrite
import net.zomis.games.dsl.flow.GameFlowRules
import net.zomis.games.dsl.flow.GameFlowScope
import net.zomis.games.scorers.ScorerFactory
import kotlin.reflect.KClass

data class ActionableOption(val actionType: String, val parameter: Any, val display: Any)
interface GameSerializable {
    fun serialize(): Any
}
interface Actionable<T : Any, A : Any> {
    val playerIndex: Int
    val game: T
    val actionType: String
    val parameter: A
}
interface EventTools {
    val eliminations: PlayerEliminationsWrite
    val replayable: ReplayableScope
    fun <E: Any> config(gameConfig: GameConfig<E>): E
}
interface GameUtils {
    val eliminations: PlayerEliminationsRead
    val replayable: ReplayableScope
    fun <E: Any> config(gameConfig: GameConfig<E>): E
}
data class Action<T : Any, A : Any>(
    override val game: T,
    override val playerIndex: Int,
    override val actionType: String,
    override val parameter: A
): Actionable<T, A>

class GameSpec<T : Any>(val name: String, val dsl: GameDsl<T>.() -> Unit) {
    operator fun invoke(context: GameDsl<T>) = dsl(context)
}
typealias GameTestDsl<T> = suspend GameTest<T>.() -> Unit
typealias GameModelDsl<T, C> = GameModel<T, C>.() -> Unit
typealias GameActionRulesDsl<T> = GameActionRules<T>.() -> Unit
typealias GameFlowRulesDsl<T> = GameFlowRules<T>.() -> Unit
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

interface GameDsl<T : Any> {
    var useRandomAI: Boolean
    @Deprecated("use GameConfig class")
    fun <C : Any> setup(configClass: KClass<C>, modelDsl: GameModelDsl<T, C>)
    fun setup(modelDsl: GameModelDsl<T, Unit>)
    fun testCase(players: Int, testDsl: GameTestDsl<T>)
    fun actionRules(actionRulesDsl: GameActionRulesDsl<T>)
    fun gameFlow(flowDsl: GameFlowDsl<T>)
    fun gameFlowRules(flowRulesDsl: GameFlowRulesDsl<T>)
    fun <E: Any> config(key: String, default: () -> E): GameConfig<E>
    val scorers: ScorerFactory<T>
}

class GameActionCreator<T : Any, A : Any>(
    override val name: String,
    override val parameterType: KClass<A>,
    override val serializedType: KClass<*>,
    val serializer: (A) -> Any,
    val deserializer: (ActionOptionsScope<T>.(Any) -> A)?
): ActionType<T, A> {
    override fun toString(): String = "(ActionType '$name' of type $parameterType)"
    override fun serialize(parameter: A): Any = serializer(parameter)
    override fun deserialize(scope: ActionOptionsScope<T>, serialized: Any): A? = deserializer?.invoke(scope, serialized)

    inline fun <reified S2: Any> serialization(noinline serializer: (A) -> S2, noinline deserializer: ActionOptionsScope<T>.(S2) -> A): GameActionCreator<T, A> {
        return GameActionCreator(name, parameterType, S2::class, serializer, deserializer as ActionOptionsScope<T>.(Any) -> A)
    }

    inline fun <reified S2: Any> serializer(noinline serializer: (A) -> S2): GameActionCreator<T, A> {
        return GameActionCreator(name, parameterType, S2::class, serializer, null)
    }

}

class GameCreator<T : Any>(val modelClass: KClass<T>) {
    fun game(name: String, dsl: GameDsl<T>.() -> Unit): GameSpec<T> = GameSpec(name, dsl)
    fun <A: Any> action(name: String, parameterType: KClass<A>): GameActionCreator<T, A>
        = GameActionCreator(name, parameterType, parameterType, {it}, {it as A})
    fun singleAction(name: String) = this.action(name, Unit::class)
}

interface ActionType<T : Any, A : Any> {
    val name: String
    val parameterType: KClass<A>
    val serializedType: KClass<*>
    fun serialize(parameter: A): Any
    fun deserialize(scope: ActionOptionsScope<T>, serialized: Any): A?
}
