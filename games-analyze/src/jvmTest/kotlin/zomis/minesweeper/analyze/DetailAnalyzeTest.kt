package zomis.minesweeper.analyze

import net.zomis.minesweeper.analyze.AnalyzeResult
import net.zomis.minesweeper.analyze.detail.DetailedResults
import net.zomis.minesweeper.analyze.detail.ProbabilityKnowledge
import net.zomis.minesweeper.analyze.factory.CharPoint
import net.zomis.minesweeper.analyze.factory.General2DAnalyze
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DetailAnalyzeTest {
    @Test
    fun detailedAnalyzeTest() {
        val input = arrayOf(
            "___",  // aaa   abc 
            "_x_",  // bcd   ded
            "2_3",  // 2c3   2f3
            "_xx",  // bcd   ded
            "xx_" // aaa   abc
        )
        val analyze = General2DAnalyze(input)
        val results: AnalyzeResult<CharPoint> = analyze.solve()
        val detail: DetailedResults<CharPoint> = results.analyzeDetailed(analyze)
        assertEquals(6, detail.proxyCount)
        val field12: ProbabilityKnowledge<CharPoint> = detail.getProxyFor(analyze.getPoint(1, 2))
        val field20: ProbabilityKnowledge<CharPoint> = detail.getProxyFor(analyze.getPoint(2, 0))
        val field03: ProbabilityKnowledge<CharPoint> = detail.getProxyFor(analyze.getPoint(0, 3))
        assertArrayEquals(
            doubleArrayOf(
                0.047619047619047616,
                0.30158730158730157,
                0.2857142857142857,
                0.07936507936507936
            ), field20.probabilities, 0.0000001
        )
        assertArrayEquals(
            doubleArrayOf(
                0.031746031746031744,
                0.2698412698412698,
                0.38095238095238093,
                0.15873015873015872,
                0.015873015873015872,
                0.0
            ), field03.probabilities, 0.0000001
        )
        assertArrayEquals(
            doubleArrayOf(0.0, 0.0, 0.0, 0.23809523809523808, 0.19047619047619047, 0.0, 0.0, 0.0, 0.0),
            field12.probabilities,
            0.0000001
        )
    }

    @Test
    fun detailed13Test() {
        val input = arrayOf(
            "________",  // aaaaaaaa   abbbbbba
            "________",  // aaaaaaaa   bcdefghb
            "_____xx_",  // aabddcaa   bijklmnb
            "___13x__",  // aab13caa   bop13qrb
            "____x___",  // aabddcaa   bijklmnb
            "____xx__",  // aaaaaaaa   bcdefghb
            "________" // aaaaaaaa   abbbbbba
        )
        val analyze = General2DAnalyze(input)
        val results: AnalyzeResult<CharPoint> = analyze.solve()
        for (ee in results.solutions) {
            println(ee)
            println()
        }
        val detail: DetailedResults<CharPoint> = results.analyzeDetailed(analyze)
        for (ee in detail.allProxies()) {
            println(ee)
            println()
        }
        assertEquals(18, detail.proxyCount)
        assertArrayEquals(
            detail.getProxyFor(analyze.getPoint(1, 1)).probabilities,
            detail.getProxyFor(analyze.getPoint(1, 5)).probabilities,
            0.000001
        )
    }
}