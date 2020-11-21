package net.zomis.games.dsl

import net.zomis.games.PlayerEliminations
import net.zomis.games.dsl.rulebased.GameRules
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

class GameSpec<T : Any>(val name: String, val dsl: GameDsl<T>.() -> Unit) {
    operator fun invoke(context: GameDsl<T>) = dsl(context)
}
typealias GameTestDsl<T> = GameTest<T>.() -> Unit
typealias GameModelDsl<T, C> = GameModel<T, C>.() -> Unit
typealias GameViewDsl<T> = GameView<T>.() -> Unit
typealias GameActionRulesDsl<T> = GameActionRules<T>.() -> Unit
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
    @Deprecated("use actionRules instead", ReplaceWith("actionRules(actionRulesDsl)"))
    fun rules(actionRulesDsl: GameActionRulesDsl<T>) { actionRules(actionRulesDsl) }
    fun testCase(players: Int, testDsl: GameTestDsl<T>)
    fun actionRules(actionRulesDsl: GameActionRulesDsl<T>)
    fun gameRules(rulesDsl: GameRulesDsl<T>)
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
        return this.serialization(S2::class, serializer, deserializer)
    }

    @Deprecated("Use reified version instead")
    fun <S2: Any> serialization(clazz: KClass<S2>, serializer: (A) -> S2, deserializer: ActionOptionsScope<T>.(S2) -> A): GameActionCreator<T, A, S2> {
        return GameActionCreator(name, parameterType, clazz, serializer, deserializer)
    }

    inline fun <reified S2: Any> serializer(noinline serializer: (A) -> S2): GameActionCreator<T, A, S2> {
        return this.serializer(S2::class, serializer)
    }

    @Deprecated("Use reified version instead")
    fun <S2: Any> serializer(clazz: KClass<S2>, serializer: (A) -> S2): GameActionCreator<T, A, S2> {
        return GameActionCreator(name, parameterType, clazz, serializer, null)
    }

}

class GameCreator<T : Any>(val modelClass: KClass<T>) {
    fun game(name: String, dsl: GameDsl<T>.() -> Unit): GameSpec<T> = GameSpec(name, dsl)
    fun <A: Any> action(name: String, parameterType: KClass<A>): GameActionCreator<T, A, A>
        = GameActionCreator(name, parameterType, parameterType, {it}, {it})
    fun singleAction(name: String) = this.action(name, Unit::class)
}

interface ActionType<T : Any, A : Any> {
    val name: String
    val parameterType: KClass<A>
    val serializedType: KClass<*>
    fun serialize(parameter: A): Any
    fun deserialize(scope: ActionOptionsScope<T>, serialized: Any): A?
}
