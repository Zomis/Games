package zomis.minesweeper.analyze

import net.zomis.minesweeper.analyze.AnalyzeResult
import net.zomis.minesweeper.analyze.FieldRule
import net.zomis.minesweeper.analyze.RuleConstraint
import net.zomis.minesweeper.analyze.factory.CharPoint
import net.zomis.minesweeper.analyze.factory.General2DAnalyze
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.*

class RootAnalyzeImplTest {
    @Test
    fun noGroups() {
        val analyze: AnalyzeResult<CharPoint> = General2DAnalyze(arrayOf("#"), 1).solve()
        assertNull(analyze.getGroupFor(CharPoint(0, 0, 'x')))
        assertEquals(0, analyze.groups.size)
    }

    @Test
    fun test() {
        val analyze = General2DAnalyze(
            arrayOf(
                "___",
                "_1_",
                "_x_"
            )
        )
        val solution: AnalyzeResult<CharPoint> = analyze.solve()
        val extraRules: MutableList<RuleConstraint<CharPoint>> = ArrayList<RuleConstraint<CharPoint>>()
        extraRules.add(
            FieldRule<CharPoint>(
                null,
                Arrays.asList<CharPoint>(analyze.getPoint(0, 0), analyze.getPoint(1, 0)),
                1
            )
        )
        assertEquals(0.25, solution.getProbabilityOf(extraRules), 0.0001)
        assertFalse(false)
    }
}