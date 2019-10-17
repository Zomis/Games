package net.zomis

class Best<T>(val maximize: Boolean) {

    private var bestValue = if (maximize) Double.NEGATIVE_INFINITY else Double.POSITIVE_INFINITY
    private var bestElements: MutableList<T> = mutableListOf()

    fun next(element: T, value: Double) {
        if (maximize) {
            if (value > bestValue) {
                bestValue = value
                bestElements = mutableListOf(element)
            } else if (value >= bestValue) {
                bestElements.add(element)
            }
        } else {
            if (value < bestValue) {
                bestValue = value
                bestElements = mutableListOf(element)
            } else if (value <= bestValue) {
                bestElements.add(element)
            }
        }
    }

    fun random(): T {
        return bestElements.random()
    }

    fun getBest(): List<T> {
        return bestElements.toList()
    }

    fun isBest(element: T): Boolean {
        return bestElements.contains(element)
    }

}