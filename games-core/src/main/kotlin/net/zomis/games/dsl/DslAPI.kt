package net.zomis.games.dsl

import net.zomis.games.PlayerEliminations
import kotlin.reflect.KClass

interface Actionable<T : Any, A : Any> {
    val playerIndex: Int
    val game: T
    val actionType: String
    val parameter: A
}
interface GameUtils {
    val playerEliminations: PlayerEliminations
    val replayable: ReplayableScope
}
data class Action<T : Any, A : Any>(
    override val game: T,
    override val playerIndex: Int,
    override val actionType: String,
    override val parameter: A
): Actionable<T, A>

typealias PlayerIndex = Int?
fun PlayerIndex.isObserver(): Boolean = this == null

typealias GameSpec<T> = GameDsl<T>.() -> Unit
typealias GameModelDsl<T, C> = GameModel<T, C>.() -> Unit
typealias GameViewDsl<T> = GameView<T>.() -> Unit
typealias GameRulesDsl<T> = GameRules<T>.() -> Unit
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

interface GameDsl<T : Any> {
    @Deprecated("to be removed, try to use Grid2D or something instead")
    fun <P> gridSpec(spec: GridDsl<T, P>): GridDsl<T, P>
    fun <C : Any> setup(configClass: KClass<C>, modelDsl: GameModelDsl<T, C>)
    fun setup(modelDsl: GameModelDsl<T, Unit>)
    fun view(viewDsl: GameViewDsl<T>)
    fun rules(rulesDsl: GameRulesDsl<T>)
}

class GameActionCreator<T : Any, A : Any, S : Any>(
    override val name: String,
    override val parameterType: KClass<A>,
    override val serializedType: KClass<S>,
    val serializer: (A) -> S,
    val deserializer: (ActionOptionsScope<T>.(S) -> A)?
): ActionType<A> {
    override fun serialize(parameter: A): Any = serializer(parameter)
    override fun <U: Any> deserialize(scope: ActionOptionsScope<U>, serialized: Any): A? = deserializer?.invoke(scope as ActionOptionsScope<T>, serialized as S)

    fun <S2: Any> serialization(clazz: KClass<S2>, serializer: (A) -> S2, deserializer: ActionOptionsScope<T>.(S2) -> A): GameActionCreator<T, A, S2> {
        return GameActionCreator(name, parameterType, clazz, serializer, deserializer)
    }

    fun <S2: Any> serializer(clazz: KClass<S2>, serializer: (A) -> S2): GameActionCreator<T, A, S2> {
        return GameActionCreator(name, parameterType, clazz, serializer, null)
    }
}

class GameCreator<T : Any>(val modelClass: KClass<T>) {
    fun game(name: String, dsl: GameDsl<T>.() -> Unit): GameSpec<T> = dsl
    fun <A: Any> action(name: String, parameterType: KClass<A>): GameActionCreator<T, A, A>
        = GameActionCreator(name, parameterType, parameterType, {it}, {it})
}

fun <T : Any> createGame(name: String, dsl: GameDsl<T>.() -> Unit): GameSpec<T> {
    return dsl
}

@Deprecated("Use GameCreator.action")
data class ActionSerialization<A : Any, T : Any>(val serialize: (A) -> Any, val deserialize: ActionOptionsScope<T>.(Any) -> A)
@Deprecated("Use GameCreator.action")
data class ActionTypeImpl<A : Any>(
    override val name: String,
    override val parameterType: KClass<A>,
    override val serializedType: KClass<*>,
    val serialize: ActionSerialization<A, Any>
): ActionType<A> {
    override fun serialize(parameter: A): Any = serialize.serialize(parameter)
    override fun <T: Any> deserialize(scope: ActionOptionsScope<T>, serialized: Any): A? = serialize.deserialize(scope as ActionOptionsScope<Any>, serialized)
}
interface ActionType<A : Any> {
    val name: String
    val parameterType: KClass<A>
    val serializedType: KClass<*>
    fun serialize(parameter: A): Any
    fun <T: Any> deserialize(scope: ActionOptionsScope<T>, serialized: Any): A?
}
@Deprecated("Use GameCreator.action")
inline fun <reified A : Any> createActionType(name: String, parameterType: KClass<A>): ActionType<A> {
    return ActionTypeImpl(name, parameterType, A::class, ActionSerialization({it}, {it as A}))
}
