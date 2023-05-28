package net.zomis.minesweeper.analyze

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Static methods for combinatorics
 *
 * @author Simon Forsberg
 */
object Combinatorics {
    /**
     * Calculates the combinations for the hypergeometric probability distribution, that is, it does not divide by `N nCr n`.
     *
     * Example: There are 6 fields, 3 of them contains a mine. You have the possibility to take 2 of these at once.<br></br>
     * `NNKK(6, 3, 2, 0)` will return the **number of combinations** where 0 of the 2 you are taking contains a mine.
     *
     * @see [Hypergeometric Distribution on Wikipedia](http://en.wikipedia.org/wiki/Hypergeometric_distribution)
     *
     *
     * @param N All elements
     * @param n All elements containing what we are looking for
     * @param K How many elements are we looking in
     * @param k How many elements we are looking in that contains what we are looking for
     * @return The number of combinations of `k` interesting elements in `K` areas when there are `n` interesting elements in `N` areas.
     */
    fun NNKK(N: Int, n: Int, K: Int, k: Int): Double {
        return nCr(K, k) * nCr(N - K, n - k)
        // Does not do the last part:	/ RootAnalyze.nCr(N, n)
    }

    fun <T> listCombination(combination: Double, size: Int, elementList: List<T>): MutableList<T>? {
        require(combination == combination.toInt().toDouble()) { "x is not an integer $combination" }
        val a = indexCombinations(combination, size, elementList.size)
            ?: return null
        val b: MutableList<T> = mutableListOf()
        for (i in a) {
            b.add(elementList[i])
        }
        return b
    }

    fun <T> multiListCombination(rules: List<FieldRule<T>>, combinationNumber: Double): List<T>? {
        var rules = rules
        if (rules.isEmpty()) {
            return emptyList()
        }
        rules = rules.toMutableList()
        val first: FieldRule<T> = rules.removeAt(0)
        var remaining = 1.0
        for (fr in rules) {
            remaining = remaining * fr.nCr()
        }
        require(combinationNumber < remaining * first.nCr()) { "Not enough combinations. " + combinationNumber + " max is " + remaining * first.nCr() }
        val combo = combinationNumber % first.nCr()
        val list = listCombination(combo, first.result, first.fieldGroups().iterator().next().fields)
        if (!rules.isEmpty()) {
            val recursive = multiListCombination(rules, floor(combinationNumber / first.nCr()))
                ?: return null
            list!!.addAll(recursive)
        }
        return list
    }

    @Deprecated("")
    fun indexCombinations(x: Double, size: Int, elements: Int): MutableList<Int>? {
        if (size < 0 || size > elements) {
            return null
        }
        if (size == 0) {
            return if (x == 0.0) mutableListOf() else null
        }
        if (size == elements) {
            return (0 until elements).toMutableList()
        }
        if (x < nCr(elements - 1, size)) {
            return indexCombinations(x, size, elements - 1)
        }
        val o = indexCombinations(x - nCr(elements - 1, size), size - 1, elements - 1)
        o?.add(elements - 1)
        return o
    }

    /**
     * Calculates the Binomial Coefficient
     *
     * @see [Binomial Coefficient on Wikipedia](http://en.wikipedia.org/wiki/Binomial_coefficient)
     *
     *
     * @param n number of elements you have
     * @param r number of elements you want to pick
     * @return number of combinations when you have `n` elements and want `r` of them
     */
    fun nCr(n: Int, r: Int): Double {
        if (r > n || r < 0) {
            return 0.0
        }
        if (r == 0 || r == n) {
            return 1.0
        }
        var value = 1.0
        for (i in 0 until r) {
            value = value * (n - i) / (r - i)
        }
        return value
    }

    private fun specificCombination(result: IntArray, combination: Double, elements: Int, size: Int) {
        var combination = combination
        var elements = elements
        var size = size
        var resultIndex = 0
        var nextNumber = 0
        while (size > 0) {
            val ncr = nCr(elements - 1, size - 1)
            if (combination <= ncr) {
                result[resultIndex] = nextNumber
                elements--
                size--
                nextNumber++
                resultIndex++
            } else {
                combination -= ncr
                elements--
                nextNumber++
            }
        }
    }

    /**
     * You have x elements and want to pick a specific combination them which will contain y elements.
     *
     *
     * For example, you have 5 elements and want 3 of them. There are 10 combinations for this. The exact combinations can be ordered as:
     * `012, 013, 014, 023, 024, 034, 123, 124, 134, 234`. Combination number 4 is then 023,
     * so `specificCombination(5, 3, 4)` will return the array `{ 0, 2, 3 }`
     *
     * @param elements number of elements you have
     * @param size number of elements you want to pick
     * @param combination the combination number you want to pick. `1 <= combinationNumber <= nCr(elements, size)`
     * @return the specific elements that you picked, each element is `0 <= value < elements`
     * @throws IllegalArgumentException if combinationNumber is out of range
     * @throws IllegalArgumentException if elements or size is negative
     */
    fun specificCombination(elements: Int, size: Int, combination: Double): IntArray {
        require(floor(combination) == ceil(combination)) { "Combination must be a whole number" }
        require(combination > 0.0) { "Combination must be positive" }
        val result = IntArray(size)
        specificCombination(result, combination, elements, size)
        return result
    }
}