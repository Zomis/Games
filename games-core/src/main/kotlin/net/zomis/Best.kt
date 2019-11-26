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

    fun asCollection(): Collection<T> = bestElements.toList()

    fun randomBest(): T = bestElements.random()
    fun getBest(): List<T> = bestElements.toList()
    fun firstBest(): T = bestElements.first()
    fun isBest(element: T): Boolean = bestElements.contains(element)
    fun getBestValue(): Double = bestValue

}