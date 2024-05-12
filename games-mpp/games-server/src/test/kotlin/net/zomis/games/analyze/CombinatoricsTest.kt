package net.zomis.games.analyze

import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import net.zomis.games.cards.probabilities.Combinatorics
import org.junit.jupiter.api.DynamicContainer
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

class CombinatoricsTest {

    val options = intArrayOf(3, 4, 2, 4)
    val combinations = Combinatorics.combinations(options)

    @Test
    fun `combinations should be 3x4x2x4`() {
        assertThat(combinations).isEqualTo(3 * 4 * 2 * 4)
    }

    @Test
    fun `combinations should be unique`() {
        val results = (0 until combinations).map {
            Combinatorics.specificPermutation(options, it).toList()
        }.toSet()
        assertThat(results.size).isEqualTo(combinations)
    }

    @TestFactory
    fun `all combinations`(): DynamicNode {
        val tests = (0 until combinations).map { combination ->
            val result = Combinatorics.specificPermutation(options, combination)
            val indicesTests = options.indices.map { index ->
                DynamicTest.dynamicTest("index $index") {
                    assertThat(result[index]).isAtLeast(0)
                    assertThat(result[index]).isAtMost(options[index] - 1)
                }
            }
            DynamicContainer.dynamicContainer("combination $combination result should be within valid range", indicesTests)
        }
        return DynamicContainer.dynamicContainer("all combs", tests)
    }

}