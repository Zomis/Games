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

}
