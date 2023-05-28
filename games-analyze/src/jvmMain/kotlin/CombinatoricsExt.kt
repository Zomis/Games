package net.zomis.minesweeper.analyze

import java.math.BigInteger

object CombinatoricsExt {
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
    fun nCrBigInt(n: Int, r: Int): BigInteger {
        var r = r
        if (r > n || r < 0) {
            return java.math.BigInteger.ZERO
        }
        if (r == 0 || r == n) {
            return java.math.BigInteger.ONE
        }
        if (r > n / 2) {
            // As Pascal's triangle is horizontally symmetric, use that property to reduce the for-loop below
            r = n - r
        }
        var value: java.math.BigInteger = java.math.BigInteger.ONE
        for (i in 0 until r) {
            value = value.multiply(java.math.BigInteger.valueOf((n - i).toLong()))
                .divide(java.math.BigInteger.valueOf((i + 1).toLong()))
        }
        return value
    }

    /**
     * You have x elements and want to pick a specific combination them which will contain y elements.
     *
     *
     * For example, you have 5 elements and want 3 of them. There are 10 combinations for this. The exact combinations can be ordered as:
     * `012, 013, 014, 023, 024, 034, 123, 124, 134, 234`. Combination number 4 is then 023,
     * so `specificCombination(5, 3, BigInteger.valueOf(4))` will return the array `{ 0, 2, 3 }`
     *
     * @param elements number of elements you have
     * @param size number of elements you want to pick
     * @param combinationNumber the combination number you want to pick. `1 <= combinationNumber <= nCr(elements, size)`
     * @return the specific elements that you picked, each element is `0 <= value < elements`
     * @throws IllegalArgumentException if combinationNumber is out of range
     * @throws IllegalArgumentException if elements or size is negative
     */
    fun specificCombination(elements: Int, size: Int, combinationNumber: java.math.BigInteger): IntArray {
        require(combinationNumber.signum() == 1) { "Combination must be positive" }
        require(!(elements < 0 || size < 0)) { "Elements and size cannot be negative" }
        val result = IntArray(size)
        var resultIndex = 0
        var nextNumber = 0
        var combination: java.math.BigInteger = combinationNumber
        var remainingSize = size
        var remainingElements = elements
        var ncr: java.math.BigInteger = nCrBigInt(remainingElements - 1, remainingSize - 1)
        while (remainingSize > 0) {
            require(ncr.signum() != 0) { "Combination out of range: $combinationNumber with $elements elements and size $size" }
            if (combination.compareTo(ncr) <= 0) {
                result[resultIndex] = nextNumber
                if (remainingElements > 1) {
                    ncr = ncr.multiply(java.math.BigInteger.valueOf((remainingSize - 1).toLong()))
                        .divide(java.math.BigInteger.valueOf((remainingElements - 1).toLong()))
                }
                remainingSize--
                resultIndex++
            } else {
                combination = combination.subtract(ncr)
                ncr = ncr.multiply(java.math.BigInteger.valueOf((remainingElements - 1 - (remainingSize - 1)).toLong()))
                    .divide(java.math.BigInteger.valueOf((remainingElements - 1).toLong()))
            }
            remainingElements--
            nextNumber++
        }
        return result
    }


}