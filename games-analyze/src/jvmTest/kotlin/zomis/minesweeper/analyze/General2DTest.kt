package zomis.minesweeper.analyze

import net.zomis.games.components.Point
import net.zomis.minesweeper.analyze.AnalyzeResult
import net.zomis.minesweeper.analyze.FieldGroup
import net.zomis.minesweeper.analyze.factory.CharPoint
import net.zomis.minesweeper.analyze.factory.General2DAnalyze
import net.zomis.minesweeper.analyze.sanityCheck
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class General2DTest {
    @Test
    fun simpleMap() {
        val input = arrayOf(
            "___x",
            "_13x",
            "__x_",
            "_xxx"
        )
        val gen2d = General2DAnalyze(input)
        val analyze: AnalyzeResult<CharPoint> = gen2d.solve()
        analyze.sanityCheck()
        val grp: FieldGroup<CharPoint> = analyze.getGroupFor(gen2d.getPoint(0, 0))!!
        assertEquals(1, grp.solutionsKnown)
        assertEquals(2, analyze.solutions.size)
        assertEquals(16 - 2, analyze.fields.size)
        assertEquals(3.0 * 6 + 4 * 3 * 4, analyze.total, 0.000001)
    }

    @Test
    fun hard12ruleTest() {
        val input = arrayOf(
            "xxxxx_xxxxx_____",
            "xxxxx_xxxxx_____",
            "xxxxx_xxxxx_____",
            "xxxxx___________",
            "________________",
            "_____x__________",
            "_____________xxx",
            "____________1232",
            "___2222x__x_1000",
            "___1001112xx1000",
            "___2000001221011",
            "__x201110000001x",
            "___202x20001111_",
            "___213x20002x___",
            "____x2110113x___",
            "_____10001x_____",
        )
        val mines = input.toList().flatMap { it.asSequence() }.count { it == 'x' }
        assertEquals(51, mines)
        val gen2d = General2DAnalyze(input)
        val analyze: AnalyzeResult<CharPoint> = gen2d.solve()
        println("Rules:")
        analyze.rules.forEach {
            println(it)
        }
        println("Solutions:")
        analyze.solutions.forEach {
            println(it)
        }
        println("Groups:")
        analyze.groups.forEach {
            println(it + " = " + it.probability)
        }

        analyze.sanityCheck()

        val grp: FieldGroup<CharPoint> = analyze.getGroupFor(gen2d.getPoint(4, 7))!!
        assertEquals(16, analyze.groups.filter { it.probability > 0.9999 }.flatten().size)
        assertEquals(1.0, grp.probability, 0.0001)
    }

    @Test
    fun time() {
        for (i in 0..999) {
            rolfl1()
        }
        val time: Long = java.lang.System.nanoTime()
        val analyze: General2DAnalyze = rolfl1()
        val timeEnd: Long = java.lang.System.nanoTime()
        println((timeEnd - time) / 1000000.0)
        println(analyze)
    }

    var input = arrayOf(
        "___x",
        "_2_x",
        "_xx_",
        "__2_",
        "x___"
    )

    fun rolfl1(): General2DAnalyze {
        val analyze = General2DAnalyze(input)
        analyze.solve()
        return analyze
    }
}