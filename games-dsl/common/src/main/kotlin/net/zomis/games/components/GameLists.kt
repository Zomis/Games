package net.zomis.games.common

fun <T: Any> T.toSingleList(): List<T> = listOf(this)
fun <T> List<T>.shifted(steps: Int): List<T> {
    val actualSteps = steps.fmod(size)
    require(actualSteps >= 0)
    require(actualSteps < this.size)
    return this.subList(actualSteps, this.size) + this.subList(0, actualSteps)
}
operator fun <T> List<T>.times(multiplier: Int): List<T> = (1 until multiplier)
    .fold(this.toList()) { acc, _ -> acc + this.toList() }

fun <K, V> MutableMap<K, V>.putSingle(key: K, v: V) {
    check(!this.containsKey(key))
    this[key] = v
}

inline fun <reified R: Any> Any?.safeCast(): R? {
    return if (this is R) this else null
}
