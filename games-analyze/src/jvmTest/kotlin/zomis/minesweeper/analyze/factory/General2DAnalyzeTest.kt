package zomis.minesweeper.analyze.factory

import net.zomis.minesweeper.analyze.AnalyzeResult
import net.zomis.minesweeper.analyze.factory.CharPoint
import net.zomis.minesweeper.analyze.factory.General2DAnalyze
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class General2DAnalyzeTest {
    private val input = arrayOf(
        "#01!#",
        "0011#",
        "00___",
        "00_2_",
        "001!_"
    )

    @Test
    fun invalidChar() {
        assertThrows<IllegalArgumentException> {
            General2DAnalyze(arrayOf("X"))
        }
    }

    @Test
    fun validChars() {
        General2DAnalyze(arrayOf("3"))
        General2DAnalyze(arrayOf("#"))
        General2DAnalyze(arrayOf("x"))
        General2DAnalyze(arrayOf("!"))
        General2DAnalyze(arrayOf("_"))
        General2DAnalyze(arrayOf("?"))
    }

    @Test
    fun testInput() {
        val gen2d = General2DAnalyze(input, -1)
        val analyze: AnalyzeResult<CharPoint> = gen2d.solve()
        assertEquals(2.0, analyze.total, 0.00001)
        assertEquals(-1, gen2d.remainingMinesCount)
        assertEquals(2, analyze.groups.size)
        assertEquals(2, analyze.originalRules.size)
        println(analyze.rules)
        assertEquals(0, analyze.rules.size)
    }

    @Test
    fun wrongNeighborCount() {
        assertThrows<IllegalArgumentException> {
            General2DAnalyze(input, 4, arrayOf(intArrayOf(1, 2, 3)))
        }
    }

    @Test
    fun blockedFieldsAreClicked() {
        val analyze = General2DAnalyze(input)
        assertTrue(analyze.isClicked(CharPoint(4, 2, '#')))
        assertFalse(analyze.isClicked(CharPoint(4, 2, '_')))
    }

    @Test
    fun knownMineTest() {
        val analyze = General2DAnalyze(input)
        assertTrue(analyze.isDiscoveredMine(CharPoint(4, 2, '!')))
        assertFalse(analyze.isDiscoveredMine(CharPoint(4, 2, '#')))
        assertFalse(analyze.isDiscoveredMine(CharPoint(4, 2, '4')))
        assertFalse(analyze.isDiscoveredMine(CharPoint(4, 2, '_')))
    }

    @Test
    fun fieldHasRuleTest() {
        val analyze = General2DAnalyze(input)
        assertFalse(analyze.fieldHasRule(CharPoint(4, 2, '_')))
        assertFalse(analyze.fieldHasRule(CharPoint(4, 2, '#')))
        assertFalse(analyze.fieldHasRule(CharPoint(4, 2, '!')))
        assertFalse(analyze.fieldHasRule(CharPoint(4, 2, 'x')))
        assertFalse(analyze.fieldHasRule(CharPoint(4, 2, '?')))
    }

    @Test
    fun neighborsTest() {
        val analyze = General2DAnalyze(input)
        var neighbors: List<CharPoint?> = analyze.getNeighbors(analyze.getPoint(0, 0))
        assertEquals(3, neighbors.size)
        assertTrue(neighbors.contains(analyze.getPoint(0, 1)))
        assertTrue(neighbors.contains(analyze.getPoint(1, 0)))
        assertTrue(neighbors.contains(analyze.getPoint(1, 1)))
        assertFalse(neighbors.contains(analyze.getPoint(2, 2)))
        neighbors = analyze.getNeighbors(analyze.getPoint(4, 4))
        assertEquals(3, neighbors.size)
        assertTrue(neighbors.contains(analyze.getPoint(3, 4)))
        assertTrue(neighbors.contains(analyze.getPoint(4, 3)))
        assertTrue(neighbors.contains(analyze.getPoint(3, 3)))
        assertFalse(neighbors.contains(analyze.getPoint(2, 2)))
    }
}