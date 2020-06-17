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
        for (i in n downTo n - r + 1) result = result * i
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
}
