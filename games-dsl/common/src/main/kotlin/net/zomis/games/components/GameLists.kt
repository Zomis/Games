package net.zomis.games.common

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

fun <T: Any> T.toSingleList(): List<T> = listOf(this)
fun <T> List<T>.shifted(steps: Int): List<T> {
    val actualSteps = steps.fmod(size)
    require(actualSteps >= 0)
    require(actualSteps < this.size)
    return this.subList(actualSteps, this.size) + this.subList(0, actualSteps)
}
operator fun <T> List<T>.times(multiplier: Int): List<T> = (1 until multiplier)
    .fold(this.toList()) { acc, _ -> acc + this.toList() }

fun wrapAroundDiff(size: Int, a: Int, b: Int): Int {
    // 1 2 3 4 5 -- diff between 4 and 1 is 2, because 4 -> 5 -> 1
    require(a in 0 until size)
    require(b in 0 until size)
    val diff = abs(a - b)
    val wrapDiff = abs(min(a, b) + size - max(a, b))
    return min(diff, wrapDiff)
}

fun <K, V> MutableMap<K, V>.putSingle(key: K, v: V) {
    check(!this.containsKey(key))
    this[key] = v
}

inline fun <reified R: Any> Any?.safeCast(): R? {
    return if (this is R) this else null
}
