package net.zomis

import kotlin.math.sign

@Deprecated("to avoid calling valueFunction too many times, use `bestOf` instead", replaceWith = ReplaceWith("bestOf"))
fun <T> Iterable<T>.bestBy(valueFunction: (T) -> Double): List<T> {
    val comparator: Comparator<T> = Comparator {a, b ->
        (valueFunction(a) - valueFunction(b)).sign.toInt()
    }
    return this.best(comparator)
}

fun <T> Iterable<T>.bestOf(valueFunction: (T) -> Double): List<T> {
    val iterator = iterator()
    if (!iterator.hasNext()) return emptyList()
    val first = iterator.next()
    var max = valueFunction.invoke(first)
    var maxElements = mutableListOf(first)
    while (iterator.hasNext()) {
        val e = iterator.next()
        val eValue = valueFunction.invoke(e)
        when (eValue.compareTo(max).sign) { // e, max here creates the descending order
            1 -> {
                max = eValue
                maxElements = mutableListOf(e)
            }
            0 -> maxElements.add(e)
        }
    }
    return maxElements
}

fun <T> Iterable<T>.best(comparator: Comparator<in T>): List<T> {
    val iterator = iterator()
    if (!iterator.hasNext()) return emptyList()
    var max = iterator.next()
    var maxElements = mutableListOf(max)
    while (iterator.hasNext()) {
        val e = iterator.next()
        when (comparator.compare(e, max).sign) { // e, max here creates the descending order
            1 -> {
                max = e
                maxElements = mutableListOf(max)
            }
            0 -> maxElements.add(e)
        }
    }
    return maxElements
}

@Deprecated("Use Iterable<T>.bestOf instead", replaceWith = ReplaceWith("bestOf"))
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

class GreedyIterator<T>(val descending: Boolean = false) {
    private val comparator = compareBy<Double> { it }.let { if (descending) it.reversed() else it }

    private var bestValue: Double = if (descending) Double.POSITIVE_INFINITY else Double.NEGATIVE_INFINITY
    private var bestElements: MutableList<T> = mutableListOf()

    fun next(value: Double, element: () -> T) {
        val compareResult = comparator.compare(value, bestValue)
        if (compareResult > 0) {
            bestValue = value
            bestElements = mutableListOf(element.invoke())
        } else if (compareResult == 0) {
            bestElements.add(element.invoke())
        }
    }

    fun asCollection(): Collection<T> = bestElements.toList()

    fun getBest(): List<T> = bestElements.toList()
    fun firstBest(): T = bestElements.first()
    fun isBest(element: T): Boolean = bestElements.contains(element)
    fun getBestValue(): Double = bestValue
    fun bestPair(): Pair<T, Double> = firstBest() to getBestValue()

}


class BestSuspending<T>(private val valueFunction: suspend (T) -> Double) {

    private var bestValue: Double = Double.NEGATIVE_INFINITY
    private var bestElements: MutableList<T> = mutableListOf()

    suspend fun next(element: T) {
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
