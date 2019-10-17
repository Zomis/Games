package net.zomis

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

    fun randomBest(): T {
        return bestElements.random()
    }

    fun getBest(): List<T> {
        return bestElements.toList()
    }

    fun firstBest(): T {
        return bestElements.first()
    }

    fun isBest(element: T): Boolean {
        return bestElements.contains(element)
    }

    fun getBestValue(): Double {
        return bestValue
    }

}