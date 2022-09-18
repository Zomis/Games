package net.zomis.games.dsl

enum class ActionCheckType {
    Precondition,
    Requires,
    Effect,
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

class ActionResult<T: Any, A: Any>(val actionable: Actionable<T, A>) {
    private val results = mutableListOf<ActionResultPart<out Any?>>()

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
}
