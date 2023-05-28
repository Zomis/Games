package zomis.minesweeper.analyze

import net.zomis.minesweeper.analyze.AnalyzeResult
import net.zomis.minesweeper.analyze.Solution
import net.zomis.minesweeper.analyze.factory.CharPoint
import net.zomis.minesweeper.analyze.factory.General2DAnalyze
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.random.Random

class SolutionTest {
    private val input = arrayOf(
        "_x__",
        "_13x",
        "___x",
        "_xxx"
    )
    private lateinit var analyze: AnalyzeResult<CharPoint>
    private lateinit var general2d: General2DAnalyze

    @BeforeEach
    fun setup() {
        general2d = General2DAnalyze(input)
        analyze = general2d.solve()
    }

    @Test
    fun solutionCount() {
        assertEquals(2, analyze!!.solutions.size)
    }

    @Test
    fun combinationMustBeIntegerValue() {
        assertThrows<IllegalArgumentException> {
            val solution: Solution<CharPoint> = findWithCombinations(4 * 3 * 4)
            solution.getCombination(0.5)
        }
    }

    @Test
    fun firstSolution() {
        val solution: Solution<CharPoint> = findWithCombinations(4 * 3 * 4)
        assertEquals(0, solution.getSetGroupValues()[analyze!!.getGroupFor(general2d.getPoint(0, 0))!!] as Int)
        assertEquals(1, solution.getSetGroupValues()[analyze!!.getGroupFor(general2d.getPoint(1, 0))!!] as Int)
        assertEquals(2, solution.getSetGroupValues()[analyze!!.getGroupFor(general2d.getPoint(3, 0))!!] as Int)
        assertEquals(3, solution.getSetGroupValues()[analyze!!.getGroupFor(general2d.getPoint(0, 3))!!] as Int)
        assertEquals(1.0 * 4 * 3 * 4, solution.combinations, 0.0001)
        assertEquals(48.0 / 66.0, solution.probability, 0.0001)
        assertEquals(6, solution.getCombination(0.0)!!.size)
        assertEquals(6, solution.getRandomSolution(Random(1L)).size)
    }

    private fun findWithCombinations(i: Int): Solution<CharPoint> {
        for (sol in analyze!!.solutions) {
            if (sol.combinations == i.toDouble()) {
                return sol
            }
        }
        throw NullPointerException("No solution found with $i combinations")
    }

    @Test
    fun secondSolution() {
        val solution: Solution<CharPoint> = findWithCombinations(6 * 3)
        assertEquals(1, solution.getSetGroupValues()[analyze!!.getGroupFor(general2d.getPoint(0, 0))!!] as Int)
        assertEquals(0, solution.getSetGroupValues()[analyze!!.getGroupFor(general2d.getPoint(1, 0))!!] as Int)
        assertEquals(3, solution.getSetGroupValues()[analyze!!.getGroupFor(general2d.getPoint(3, 0))!!] as Int)
        assertEquals(2, solution.getSetGroupValues()[analyze!!.getGroupFor(general2d.getPoint(0, 3))!!] as Int)
        assertEquals(3.0 * 1 * 1 * 6, solution.combinations, 0.0001)
        assertEquals(18.0 / 66.0, solution.probability, 0.0001)
    }
}