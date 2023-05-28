package zomis.alberi

import net.zomis.minesweeper.analyze.AnalyzeFactory
import net.zomis.minesweeper.analyze.AnalyzeResult
import net.zomis.minesweeper.analyze.FieldGroup
import net.zomis.minesweeper.analyze.FieldRule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AlberiTest {
    @Test
    fun simpleMap() {
        val map = """
            1112
            1322
            3342
            3444
            """.trimIndent()
        val analyze: AnalyzeFactory<String> = alberi(map, 1)
        for (rule in analyze.getRules()) {
            println(rule)
        }
        val result: AnalyzeResult<String> = analyze.solve()
        println(result.solutions)
        assertSolution(result, "10", "02", "23", "31")
    }

    @Test
    fun doubleTrees() {
        val map = """
            12223333
            11214343
            51114444
            55554444
            55554444
            56657777
            66667877
            88888888
            """.trimIndent()
        val analyze: AnalyzeFactory<String> = alberi(map, 2)
        val result: AnalyzeResult<String> = analyze.solve()
        for (group in result.groups) {
            if (group.probability > 0) {
                println(group)
            }
        }
        assertSolution(
            result, "45", "47", "04", "06", "51", "53",
            "10", "12", "65", "67", "24", "26",
            "71", "73", "30", "32"
        )
    }

    @Test
    fun someMap() {
        val map = """
            1122233
            1222223
            4255667
            4455667
            4445557
            4455577
            4455577
            """.trimIndent()
        val analyze: AnalyzeFactory<String> = alberi(map, 1)
        val result: AnalyzeResult<String> = analyze.solve()
        println(result.solutions)
        assertSolution(result, "12", "00", "61", "24", "36", "43", "55")
    }

    fun alberi(map: String, count: Int): AnalyzeFactory<String> {
        val factory: AnalyzeFactory<String> = AnalyzeFactory<String>()
        val lines = map.split("\\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val groups: MutableMap<Char, MutableList<String>> = mutableMapOf()
        for (y in lines.indices) {
            val line = lines[y]
            require(line.length == lines.size) {
                "Map format incorrect: " + map + " expected " +
                        lines.size + " columns in row " + y + " but was " + line.length
            }
            val row: MutableList<String> = java.util.ArrayList<String>()
            for (x in 0 until line.length) {
                val ch = line[x]
                addToGroup(groups, ch, x, y)
                row.add(str(x, y))
            }
            factory.addRule(FieldRule<String>("row $y", row, count))
        }
        for (x in lines.indices) {
            val col: MutableList<String> = java.util.ArrayList<String>()
            for (y in lines.indices) {
                col.add(str(x, y))
            }
            factory.addRule(FieldRule<String>("col $x", col, count))
        }
        for ((key, value) in groups) {
            factory.addRule(FieldRule<String>(key.toString(), value, count))
        }
        factory.setListener(AlberiListener())
        return factory
    }

    private fun addToGroup(groups: MutableMap<Char, MutableList<String>>, ch: Char, x: Int, y: Int) {
        var list = groups[ch]
        if (list == null) {
            list = java.util.ArrayList<String>()
            groups[ch] = list
        }
        list.add(str(x, y))
    }

    companion object {
        fun <T> assertSolution(result: AnalyzeResult<T>, vararg trueFields: T) {
            val allGroups: Set<FieldGroup<T>> = java.util.HashSet<FieldGroup<T>>(result.groups)
            val trueGroups: MutableSet<FieldGroup<T>> = java.util.HashSet<FieldGroup<T>>()
            for (field in trueFields) {
                val group: FieldGroup<T> = result.getGroupFor(field)!!
                assertEquals(1.0, group.probability, 0.0001, "Unexpected probability for $group")
                trueGroups.add(group)
            }
            val falseGroups: MutableSet<FieldGroup<T>> = java.util.HashSet<FieldGroup<T>>(allGroups)
            falseGroups.removeAll(trueGroups)
            for (group in falseGroups) {
                assertEquals(0.0, group.probability, 0.0001, "Unexpected probability for $group")
                trueGroups.add(group)
            }
        }

        fun str(x: Int, y: Int): String {
            val radix: Int = Character.MAX_RADIX
            return x.toString(radix) + y.toString(radix)
        }
    }
}