package zomis.minesweeper.analyze

import net.zomis.minesweeper.analyze.Combinatorics
import net.zomis.minesweeper.analyze.CombinatoricsExt
import net.zomis.minesweeper.analyze.FieldRule
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigInteger

class CombinatoricsTest {
    @Test
    fun nCr() {
        assertEquals(6.0, Combinatorics.nCr(4, 2), 0.0001)
        assertEquals(2.034388346356e54, Combinatorics.nCr(256, 51), 1e44)
    }

    @Test
    fun ncrAndNcrCombinations() {
        val elements = 4
        val size = 2
        val expected = arrayOf(arrayOf(0, 1), arrayOf(0, 2), arrayOf(1, 2), arrayOf(0, 3), arrayOf(1, 3), arrayOf(2, 3))
        val ncr = Combinatorics.nCr(elements, size)
        var i = 0
        while (i < ncr) {
            val list: List<Int>? = Combinatorics.indexCombinations(i.toDouble(), size, elements)
            assertArrayEquals(expected[i], list!!.toTypedArray(), "failed on $i")
            i++
        }
    }

    @Test
    fun specificCombinationVeryBig() {
        val result = CombinatoricsExt.specificCombination(256, 51, BigInteger.valueOf(Long.MAX_VALUE - 42))
        assertArrayEquals(
            intArrayOf(
                0,
                1,
                2,
                3,
                4,
                5,
                6,
                7,
                8,
                9,
                10,
                11,
                12,
                13,
                14,
                15,
                16,
                17,
                18,
                19,
                20,
                21,
                22,
                23,
                24,
                25,
                26,
                27,
                28,
                29,
                30,
                31,
                32,
                33,
                34,
                35,
                36,
                37,
                38,
                52,
                73,
                94,
                99,
                132,
                163,
                169,
                179,
                190,
                214,
                227,
                230
            ), result
        )
    }

    @Test
    fun specificCombinations() {
        assertArrayEquals(intArrayOf(0, 1, 2, 3), CombinatoricsExt.specificCombination(8, 4, BigInteger.ONE))
        assertArrayEquals(
            intArrayOf(0, 1, 2, 4),
            CombinatoricsExt.specificCombination(8, 4, BigInteger.valueOf(2))
        )
        assertArrayEquals(
            intArrayOf(0, 2, 4, 5),
            CombinatoricsExt.specificCombination(8, 4, BigInteger.valueOf(20))
        )
        assertArrayEquals(
            intArrayOf(1, 4, 6),
            CombinatoricsExt.specificCombination(7, 3, BigInteger.valueOf(24))
        )
        assertArrayEquals(
            intArrayOf(1, 5, 6),
            CombinatoricsExt.specificCombination(7, 3, BigInteger.valueOf(25))
        )
        assertArrayEquals(intArrayOf(), CombinatoricsExt.specificCombination(7, 0, BigInteger.ONE))
    }

    @Test
    fun alwaysIncreasingCombination() {
        val n = 10
        val r = 5
        val combinations: Long = CombinatoricsExt.nCrBigInt(n, r).longValueExact()
        var value: Long = 0
        for (i in 1..combinations) {
            val result = CombinatoricsExt.specificCombination(n, r, BigInteger.valueOf(i))
            println(result.contentToString())
            var arrayValues = ""
            for (index in result.indices) {
                arrayValues = arrayValues + result[index]
            }
            val nextValue = arrayValues.toLong()
            assertTrue(nextValue > value) { "nextValue $nextValue was not greater than value $value at $i of $combinations" }
            value = nextValue
        }
    }

    @Test
    fun specificCombinationOutOfRange() {
        assertThrows<IllegalArgumentException> {
            CombinatoricsExt.specificCombination(7, 3, BigInteger.valueOf(36))
        }
    }

    @Test
    fun specificCombinationZeroOrLess() {
        assertThrows<IllegalArgumentException> {
            CombinatoricsExt.specificCombination(7, 3, BigInteger.ZERO)
        }
    }

    @Test
    fun specificCombinationWithNegativeSize() {
        assertThrows<IllegalArgumentException> {
            CombinatoricsExt.specificCombination(7, -1, BigInteger.ONE)
        }
    }

    @Test
    fun specificCombinationWithTooBigSize() {
        assertThrows<IllegalArgumentException> {
            CombinatoricsExt.specificCombination(7, 8, BigInteger.ONE)
        }
    }

    @Test
    fun specificCombinationWithNegativeElements() {
        assertThrows<IllegalArgumentException> {
            CombinatoricsExt.specificCombination(-1, 3, BigInteger.ONE)
        }
    }

    @Test
    fun nCrBigInt() {
        assertEquals(BigInteger.valueOf(28), CombinatoricsExt.nCrBigInt(8, 2))
        assertEquals(BigInteger.valueOf(28), CombinatoricsExt.nCrBigInt(8, 6))
        assertEquals(BigInteger.valueOf(70), CombinatoricsExt.nCrBigInt(8, 4))
        assertEquals(BigInteger.valueOf(56), CombinatoricsExt.nCrBigInt(8, 3))
        assertEquals(BigInteger.valueOf(35), CombinatoricsExt.nCrBigInt(7, 3))
        assertEquals(BigInteger.ZERO, CombinatoricsExt.nCrBigInt(1, -1))
        assertEquals(BigInteger.ZERO, CombinatoricsExt.nCrBigInt(0, 1))
        for (i in 0..99) {
            assertEquals(BigInteger.ONE, CombinatoricsExt.nCrBigInt(i, 0))
            assertEquals(BigInteger.ONE, CombinatoricsExt.nCrBigInt(i, i))
        }
    }

    @Test
    fun pickCombinationsFromList() {
        val rules: MutableList<FieldRule<Char>> = mutableListOf()
        rules.add(FieldRule(null, mutableListOf('a', 'b'), 1))
        rules.add(FieldRule(null, mutableListOf('c', 'd', 'e'), 2))
        rules.add(FieldRule(null, mutableListOf('f', 'g'), 0))
        var ncr = 1
        for (fr in rules) {
            ncr *= fr.nCr().toInt()
        }
        assertEquals(6, ncr)
        assertEquals(mutableListOf('a', 'c', 'd'), Combinatorics.multiListCombination(rules, 0.0))
        assertEquals(mutableListOf('b', 'c', 'd'), Combinatorics.multiListCombination(rules, 1.0))
        assertEquals(mutableListOf('a', 'c', 'e'), Combinatorics.multiListCombination(rules, 2.0))
        assertEquals(mutableListOf('b', 'c', 'e'), Combinatorics.multiListCombination(rules, 3.0))
        assertEquals(mutableListOf('a', 'd', 'e'), Combinatorics.multiListCombination(rules, 4.0))
        assertEquals(mutableListOf('b', 'd', 'e'), Combinatorics.multiListCombination(rules, 5.0))
    }
}