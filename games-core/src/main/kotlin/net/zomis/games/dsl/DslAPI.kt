package net.zomis.games.dsl

import net.zomis.games.PlayerEliminationsRead
import net.zomis.games.api.Games
import net.zomis.games.dsl.flow.GameFlowRules
import net.zomis.games.dsl.flow.GameFlowScope
import kotlin.reflect.KClass

data class ActionableOption(val actionType: String, val parameter: Any, val display: Any)
interface Actionable<T : Any, A : Any> {
    val playerIndex: Int
    val game: T
    val actionType: String
    val parameter: A
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
typealias GameViewDsl<T> = GameView<T>.() -> Unit
typealias GameActionRulesDsl<T> = GameActionRules<T>.() -> Unit
typealias GameFlowRulesDsl<T> = GameFlowRules<T>.() -> Unit
typealias GameFlowDsl<T> = suspend GameFlowScope<T>.() -> Unit
typealias GridDsl<T, P> = GameGrid<T, P>.() -> Unit

@Deprecated("to be removed, try to use Grid2D or something instead")
interface GameGrid<T, P> {
    val model: T
    fun size(sizeX: Int, sizeY: Int)
    fun getter(getter: (x: Int, y: Int) -> P)
}

interface GridSpec<T, P> {
    val sizeX: (T) -> Int
    val sizeY: (T) -> Int
    fun get(model: T, x: Int, y: Int): P
}

interface GameConfig<E: Any> {
    fun mutable(): GameConfig<E>
    fun withDefaults(): GameConfig<E>

    val key: String
    val clazz: KClass<E>
    val default: () -> E
    var value: E
}
class GameConfigs(val configs: List<GameConfig<Any>>) {
    fun <E: Any> get(config: GameConfig<E>): E = configs.first { it.key == config.key }.value as E
    fun set(key: String, value: Any) {
        this.configs.first { it.key == key }.value = value
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
    @Deprecated("to be removed, try to use Grid2D or something instead")
    fun <P> gridSpec(spec: GridDsl<T, P>): GridDsl<T, P>
    fun <C : Any> setup(configClass: KClass<C>, modelDsl: GameModelDsl<T, C>)
    fun setup(modelDsl: GameModelDsl<T, Unit>)
    @Deprecated("use rules instead")
    fun view(viewDsl: GameViewDsl<T>)
    fun testCase(players: Int, testDsl: GameTestDsl<T>)
    fun actionRules(actionRulesDsl: GameActionRulesDsl<T>)
    fun gameFlow(flowDsl: GameFlowDsl<T>)
    fun gameFlowRules(flowRulesDsl: GameFlowRulesDsl<T>)
    fun <E: Any> config(key: String, default: () -> E): GameConfig<E>
}

class GameActionCreator<T : Any, A : Any, S : Any>(
    override val name: String,
    override val parameterType: KClass<A>,
    override val serializedType: KClass<S>,
    val serializer: (A) -> S,
    val deserializer: (ActionOptionsScope<T>.(S) -> A)?
): ActionType<T, A> {
    override fun serialize(parameter: A): Any = serializer(parameter)
    override fun deserialize(scope: ActionOptionsScope<T>, serialized: Any): A? = deserializer?.invoke(scope, serialized as S)

    inline fun <reified S2: Any> serialization(noinline serializer: (A) -> S2, noinline deserializer: ActionOptionsScope<T>.(S2) -> A): GameActionCreator<T, A, S2> {
        return GameActionCreator(name, parameterType, S2::class, serializer, deserializer)
    }

    inline fun <reified S2: Any> serializer(noinline serializer: (A) -> S2): GameActionCreator<T, A, S2> {
        return GameActionCreator(name, parameterType, S2::class, serializer, null)
    }

}

class GameCreator<T : Any>(val modelClass: KClass<T>) {
    fun game(name: String, dsl: GameDsl<T>.() -> Unit): GameSpec<T> = GameSpec(name, dsl)
    fun <A: Any> action(name: String, parameterType: KClass<A>): GameActionCreator<T, A, A>
        = GameActionCreator(name, parameterType, parameterType, {it}, {it})
    fun singleAction(name: String) = this.action(name, Unit::class)
    val components = Games.components
}

interface ActionType<T : Any, A : Any> {
    val name: String
    val parameterType: KClass<A>
    val serializedType: KClass<*>
    fun serialize(parameter: A): Any
    fun deserialize(scope: ActionOptionsScope<T>, serialized: Any): A?
}
