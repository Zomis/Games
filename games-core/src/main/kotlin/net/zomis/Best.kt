package net.zomis

import kotlin.math.sign

fun <T> Iterable<T>.bestBy(valueFunction: (T) -> Double): List<T> {
    val comparator: Comparator<T> = Comparator {a, b ->
        (valueFunction(a) - valueFunction(b)).sign.toInt()
    }
    return this.best(comparator)
}

fun <T> Iterable<T>.best(comparator: Comparator<in T>): List<T> {
    val iterator = iterator()
    if (!iterator.hasNext()) return emptyList()
    var max = iterator.next()
    var maxElements = mutableListOf(max)
    while (iterator.hasNext()) {
        val e = iterator.next()
        when (comparator.compare(e, max).sign) {
            1 -> {
                max = e
                maxElements = mutableListOf(max)
            }
            0 -> maxElements.add(e)
        }
    }
    return maxElements
}

@Deprecated("Use Iterable<T>.best instead")
class Best<T>(private val valueFunction: (T) -> Double) {

    private var bestValue: Double = Double.NEGATIVE_INFINITY
    private var bestElements: MutableList<T> = mutableListOf()

    fun next(element: T) {
        val value = valueFunction(element)
        if (value > bestValue) {
            bestValue = value
            bestElements = mutableListOf(element)
        } else if (value >= bestValue) {
            bestElements.add(element)
        }
    }

    fun asCollection(): Collection<T> = bestElements.toList()

    fun getBest(): List<T> = bestElements.toList()
    fun firstBest(): T = bestElements.first()
    fun isBest(element: T): Boolean = bestElements.contains(element)
    fun getBestValue(): Double = bestValue

}