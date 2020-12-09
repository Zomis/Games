package net.zomis.games.cards.probabilities

enum class CountStyle {
    EQUAL, NOT_EQUAL, MORE_THAN, LESS_THAN, UNKNOWN, DONE
}

object Combinatorics {
    fun NNKKnoDiv(N: Int, n: Int, K: Int, k: Int): Double {
        return nCr(K, k) * nCr(N - K, n - k)
    }

    fun NNKKdistribution(N: Int, n: Int, K: Int): DoubleArray? {
        val nnkkArray = DoubleArray(K + 1)
        for (i in 0..K) {
            nnkkArray[i] = NNKKwithDiv(N, n, K, i)
        }
        return nnkkArray
    }

    fun NNKKwithDiv(N: Int, n: Int, K: Int, k: Int): Double {
        return NNKKnoDiv(N, n, K, k) / nCr(N, n)
    }

    fun nPr(n: Int, r: Int): Double {
        var result = 1.0
        for (i in n downTo n - r + 1) result *= i
        return result
    }

    fun nCr(n: Int, r: Int): Double {
        if (r > n || r < 0) return 0.0
        if (r == 0 || r == n) return 1.0
        var start = 1.0
        for (i in 0 until r) {
            start = start * (n - i) / (r - i)
        }
        return start
    }

    fun specificCombination(elements: Int, size: Int, combinationNumber: Double): IntArray {
        require(combinationNumber > 0) { "Combination must be positive" }
        require(!(elements < 0 || size < 0)) { "Elements and size cannot be negative" }
        val result = IntArray(size)
        var resultIndex = 0
        var nextNumber = 0
        var combination = combinationNumber
        var remainingSize = size
        var remainingElements = elements
        while (remainingSize > 0) {
            val ncr = nCr(remainingElements - 1, remainingSize - 1)
            require(ncr > 0) { "Combination out of range: $combinationNumber with $elements elements and size $size" }
            if (combination.compareTo(ncr) <= 0) {
                result[resultIndex] = nextNumber
                remainingSize--
                resultIndex++
            } else {
                combination -= ncr
            }
            remainingElements--
            nextNumber++
        }
        return result
    }

    fun specificPermutation(elements: Int, combinationNumber: Int): IntArray {
        require(elements >= 1)
        require(combinationNumber >= 0) { "combination number must be >= 0" }
        val factorial = nPr(elements, elements)
        require(combinationNumber < factorial) { "combination number must be < factorial(elements) ($factorial)" }
        val result = IntArray(elements)
        var remainingCombinationNumber = combinationNumber
        val numbers = (0 until elements).toMutableList()
        for (i in 0 until elements) {
            val elementsRemaining = elements - i
            val div = remainingCombinationNumber / elementsRemaining
            val mod = remainingCombinationNumber % elementsRemaining
            result[i] = numbers[mod]
            numbers.removeAt(mod)
            remainingCombinationNumber = div
        }
        return result
    }

}
