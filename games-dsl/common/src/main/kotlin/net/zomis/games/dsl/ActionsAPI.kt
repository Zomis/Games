package net.zomis.games.dsl

import kotlin.reflect.KClass

enum class ActionCheckType {
    Precondition,
    Requires,
    Effect,
}

interface Actionable<T : Any, A : Any> {
    val playerIndex: Int
    val game: T
    val actionType: String
    val parameter: A
}

data class Action<T : Any, A : Any>(
    override val game: T,
    override val playerIndex: Int,
    override val actionType: String,
    override val parameter: A
): Actionable<T, A>

interface ActionType<T : Any, A : Any> {
    val name: String
    val parameterType: KClass<A>
    val serializedType: KClass<*>
    fun serialize(parameter: A): Any
    fun deserialize(scope: ActionOptionsScope<T>, serialized: Any): A?
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

    fun withName(name: String) = GameActionCreator(name, parameterType, serializedType, serializer, deserializer)

    inline fun <reified S2: Any> serialization(noinline serializer: (A) -> S2, noinline deserializer: ActionOptionsScope<T>.(S2) -> A): GameActionCreator<T, A> {
        return GameActionCreator(name, parameterType, S2::class, serializer, deserializer as ActionOptionsScope<T>.(Any) -> A)
    }

    inline fun <reified S2: Any> serializer(noinline serializer: (A) -> S2): GameActionCreator<T, A> {
        return GameActionCreator(name, parameterType, S2::class, serializer, null)
    }

}

class ActionResultPart<E>(
    val type: ActionCheckType,
    val key: Any,
    val value: E?,
    val result: Any?
) {
    val approved = when (result) {
        null -> true
        Unit -> true
        is String -> result.isNotBlank()
        is Collection<*> -> result.isNotEmpty()
        is Array<*> -> result.isNotEmpty()
        is Boolean -> result
        else -> false
    }
    val deniedResult = if (approved) null else result

    override fun toString(): String = "Key $key. Value $value returned $result"
}

class ActionResult<T: Any, A: Any>(
    val actionable: Actionable<T, A>,
    val actionType: ActionType<T, A>?
) {
    private val results = mutableListOf<ActionResultPart<out Any?>>()
    val allowed: Boolean get()
        = results.filter { it.type == ActionCheckType.Precondition || it.type == ActionCheckType.Requires }
            .all { it.approved }

    fun add(resultPart: ActionResultPart<out Any?>) {
        results.add(resultPart)
    }

    private fun add(type: ActionCheckType, key: Any, value: Any?, result: Any?)
        = add(ActionResultPart(type, key, value, result))

    fun addPrecondition(key: Any, value: Any?, result: Any?)
        = add(ActionCheckType.Precondition, key, value, result)

    fun addRequires(key: Any, value: Any?, result: Any?)
        = add(ActionCheckType.Requires, key, value, result)

    fun addEffect(key: Any, value: Any?, result: Any?)
        = add(ActionCheckType.Effect, key, value, result)

    override fun toString(): String = "Results for $actionable: ${results.groupBy { it.type }}"
    fun addAll(other: ActionResult<T, A>) {
        this.results.addAll(other.results)
    }
}
