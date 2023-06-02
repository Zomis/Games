package zomis.minesweeper.analyze

import net.zomis.minesweeper.analyze.AnalyzeFactory
import net.zomis.minesweeper.analyze.FieldGroup
import net.zomis.minesweeper.analyze.FieldRule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class AnalyzeTest {
    @Test
    fun ruleTest() {
        val root = AnalyzeFactory<String>()
        val ruleA = FieldRule("A", mutableListOf("a", "b", "c", "d"), 4)
        assertEquals("(a + b + c + d) = 4", ruleA.toString())
        assertEquals("A", ruleA.cause)
        root.addRule(ruleA)
        root.addRule(FieldRule("B", mutableListOf("c", "d", "e", "f"), 2))
        root.solve()
    }

    @Test
    fun aWholeLotOfConnectedThreesComplexMap() {
        val before = AnalyzeFactory<String>()
        val openSea = addBoard(16, 16)
        before.addRule(placeThreeAt(1, 1, openSea))
        before.addRule(placeThreeAt(9, 1, openSea))
        before.addRule(placeThreeAt(13, 1, openSea))
        before.addRule(placeThreeAt(3, 3, openSea))
        before.addRule(placeThreeAt(7, 3, openSea))
        before.addRule(placeThreeAt(11, 3, openSea))
        before.addRule(placeThreeAt(5, 5, openSea))
        before.addRule(placeThreeAt(7, 7, openSea))
        before.addRule(placeThreeAt(11, 7, openSea))
        before.addRule(placeThreeAt(1, 9, openSea))
        before.addRule(placeThreeAt(9, 9, openSea))
        before.addRule(placeThreeAt(3, 11, openSea))
        before.addRule(placeThreeAt(7, 11, openSea))
        before.addRule(placeThreeAt(11, 11, openSea))
        before.addRule(placeThreeAt(1, 13, openSea))
        before.addRule(placeThreeAt(5, 13, openSea))
        before.addRule(placeThreeAt(13, 13, openSea))
        assertEquals(256 - 17, openSea.size)
        val globalRule = FieldRule("global", openSea, 51)
        before.addRule(globalRule)
        assertEquals(51, globalRule.result)
        AnalyzeFactory.splitFieldRules(before.getRules())
        val time: Long = java.lang.System.nanoTime()
        val root = before.solve()
        val timeEnd: Long = java.lang.System.nanoTime()
        for (ee in root.groups) println(ee.toString() + ": " + ee.probability)

        // 17 '3's, 16 connections between them, 1 group for the "open sea"
        assertEquals(17 + 1, root.originalRules.size)
        assertEquals(17 + 16 + 1, root.groups.size)

        // Alright, I gotta admit... I have not calculated these numbers by hand to make sure they're correct!
        assertEquals(61440, root.solutions.size)
        println(root.solutions.iterator().next())
        assertEquals(8.268912597471693e36, root.total, 1e25)
        assertEquals(0.08875309776194928, root.getGroupFor("ff")!!.probability, EPSILON)
        assertEquals(0.2707636258317718, root.getGroupFor("23")!!.probability, EPSILON)
        assertEquals(0.26833148025906883, root.getGroupFor("80")!!.probability, EPSILON)
        assertEquals(51, globalRule.result)
        assertEquals(openSea.size, globalRule.fieldsCountInGroups)
        assertEquals(1, globalRule.smallestFieldGroup!!.size)
        val timeElapsedNanos = timeEnd - time
        println("Solve took " + timeElapsedNanos / 1000000.0)
    }

    private fun pos(x: Int, y: Int): String {
        return java.lang.Integer.toString(x, 16) + java.lang.Integer.toString(y, 16)
    }

    private fun placeThreeAt(x: Int, y: Int, openSea: MutableList<String>): FieldRule<String> {
        val fields: MutableList<String> = ArrayList<String>()
        for (xx in x - 1..x + 1) {
            for (yy in y - 1..y + 1) {
                if (xx != x || yy != y) {
                    fields.add(pos(xx, yy))
                }
            }
        }
        assertEquals(8, fields.size)
        openSea.remove(pos(x, y))
        return FieldRule(pos(x, y), fields, 3)
    }

    private fun addBoard(width: Int, height: Int): MutableList<String> {
        val pos: MutableList<String> = ArrayList<String>(width * height)
        for (x in 0 until width) {
            for (y in 0 until width) {
                pos.add(pos(x, y))
            }
        }
        return pos
    }

    @Test
    @Disabled
    fun pattern_14_withSameCharacters_preSplitted() {
//		abbc
//		a14c
//		abbc
//		dddd
        val before = AnalyzeFactory<String>()
        val a = FieldGroup(listOf("a", "a", "a"))
        val b = FieldGroup(listOf("b", "b", "b", "b"))
        val c = FieldGroup(listOf("c", "c", "c"))
        val d = FieldGroup(listOf("d", "d", "d", "d"))

        val ruleGlobal = FieldRule("global", d, 6).also { it.fieldGroups().addAll(listOf(a, b, c)) }
        val rule1 = FieldRule("1", a, 1).also { it.fieldGroups().add(b) }
        val rule4 = FieldRule("4", c, 4).also { it.fieldGroups().add(b) }

        before.addRule(ruleGlobal)
        before.addRule(rule1)
        before.addRule(rule4)
        val root = before.solve()
        assertEquals(3, root.originalRules.size)
        assertEquals(4, root.groups.size)
        assertEquals(1, root.solutions.size)
        assertEquals(3, root.getGroupFor("a")!!.size)
        assertEquals(4, root.getGroupFor("b")!!.size)
        assertEquals(3, root.getGroupFor("c")!!.size)
        assertEquals(4, root.getGroupFor("d")!!.size)
        assertEquals(0.0, root.getGroupFor("a")!!.probability, EPSILON)
        assertEquals(0.25, root.getGroupFor("b")!!.probability, EPSILON)
        assertEquals(1.0, root.getGroupFor("c")!!.probability, EPSILON)
        assertEquals(0.5, root.getGroupFor("d")!!.probability, EPSILON)
        assertEquals(1, root.solutions.size)
        val solution = root.solutions.iterator().next()
        val values = solution.getSetGroupValues()
        assertEquals(0, values[root.getGroupFor("a")!!] as Int)
        assertEquals(1, values[root.getGroupFor("b")!!] as Int)
        assertEquals(3, values[root.getGroupFor("c")!!] as Int)
        assertEquals(2, values[root.getGroupFor("d")!!] as Int)
        assertEquals(4.0 * 6, root.total, EPSILON) // 4 for 'b', 1 for 'a' (no mines in a), 6 for 'd'
    }

    @Test
    @Disabled
    fun pattern_14_withSameCharacters_autoSplit() {
//		abbc
//		a14c
//		abbc
//		dddd
        val before = AnalyzeFactory<String>()
        before.addRule(
            FieldRule(
                "global",
                mutableListOf("a", "b", "b", "c", "a", "c", "a", "b", "b", "c", "d", "d", "d", "d"),
                6
            )
        )
        before.addRule(FieldRule("1", mutableListOf("a", "b", "b", "a", "a", "b", "b"), 1))
        before.addRule(FieldRule("4", mutableListOf("b", "b", "c", "c", "b", "b", "c"), 4))
        val root = before.solve()
        assertEquals(3, root.originalRules.size)
        assertEquals(4, root.groups.size)
        assertEquals(1, root.solutions.size)
        assertEquals(3, root.getGroupFor("a")!!.size)
        assertEquals(4, root.getGroupFor("b")!!.size)
        assertEquals(3, root.getGroupFor("c")!!.size)
        assertEquals(4, root.getGroupFor("d")!!.size)
        assertEquals(0.0, root.getGroupFor("a")!!.probability, EPSILON)
        assertEquals(0.25, root.getGroupFor("b")!!.probability, EPSILON)
        assertEquals(1.0, root.getGroupFor("c")!!.probability, EPSILON)
        assertEquals(0.5, root.getGroupFor("d")!!.probability, EPSILON)
        assertEquals(1, root.solutions.size)
        val solution = root.solutions.iterator().next()
        val values = solution.getSetGroupValues()
        assertEquals(0, values[root.getGroupFor("a")!!] as Int)
        assertEquals(1, values[root.getGroupFor("b")!!] as Int)
        assertEquals(3, values[root.getGroupFor("c")!!] as Int)
        assertEquals(2, values[root.getGroupFor("d")!!] as Int)
        assertEquals(4.0 * 6, root.total, EPSILON) // 4 for 'b', 1 for 'a' (no mines in a), 6 for 'd'
    }

    @Test
    fun pattern_13() {
//		abcd
//		e13f
//		ghij
//		klmn
        // Total of 6 mines

        /* Possible solutions:
		 * b+c+h+i = 0:			1
		 * 	a+e+g = 1			3
		 * 	d+f+j = 3			1
		 * k+l+m+n = 2			6
		 * 
		 * b+c+h+i = 1:			4
		 * 	a+e+g = 0			1
		 * 	d+f+j = 2			3
		 * k+l+m+n = 3			4
		 **/
        val before = AnalyzeFactory<String>()
        before.addRule(
            FieldRule(
                "global",
                mutableListOf("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n"),
                6
            )
        )
        before.addRule(FieldRule("1", mutableListOf("a", "b", "c", "e", "g", "h", "i"), 1))
        before.addRule(FieldRule("3", mutableListOf("b", "c", "d", "f", "h", "i", "j"), 3))
        val root = before.solve()
        assertEquals(3, root.originalRules.size)
        assertEquals(4, root.groups.size)
        assertEquals(2, root.solutions.size) // b+c+h+i can either be 0 or 1.
        assertEquals(FieldGroup(listOf("a", "e", "g")), root.getGroupFor("a"))
        assertEquals(FieldGroup(listOf("b", "c", "h", "i")), root.getGroupFor("b"))
        assertEquals(FieldGroup(listOf("d", "f", "j")), root.getGroupFor("d"))
        assertEquals(FieldGroup(listOf("k", "l", "m", "n")), root.getGroupFor("k"))
        assertEquals(2, root.solutions.size)
        assertEquals(0.7575757575757576, root.getGroupFor("d")!!.probability, EPSILON)
        assertEquals(0.6818181818181818, root.getGroupFor("k")!!.probability, EPSILON)
        assertEquals(0.0909090909090909, root.getGroupFor("a")!!.probability, EPSILON)
        assertEquals(0.1818181818181818, root.getGroupFor("b")!!.probability, EPSILON)
        val solutions = root.solutions.iterator()
        val solution = solutions.next()
        var values = solution.getSetGroupValues()
        // Solution 1
        assertEquals(0, values[root.getGroupFor("a")!!] as Int)
        assertEquals(1, values[root.getGroupFor("b")!!] as Int)
        assertEquals(2, values[root.getGroupFor("d")!!] as Int)
        assertEquals(3, values[root.getGroupFor("k")!!] as Int)

        // Solution 2
        values = solutions.next().getSetGroupValues()
        assertEquals(1, values[root.getGroupFor("a")!!] as Int)
        assertEquals(0, values[root.getGroupFor("b")!!] as Int)
        assertEquals(3, values[root.getGroupFor("d")!!] as Int)
        assertEquals(2, values[root.getGroupFor("k")!!] as Int)
        assertEquals(3.0 * 6 + 4 * 3 * 4, root.total, EPSILON)
    }

    @Test
    fun createFieldRuleUsingFieldsString() {
        val rule = FieldRule("global", fields("abc"), 1)
        assertEquals(1, rule.fieldGroups().size)
        assertEquals(3, rule.smallestFieldGroup!!.size)
        assertTrue(rule.smallestFieldGroup!!.contains("a"))
        assertTrue(rule.smallestFieldGroup!!.contains("b"))
        assertTrue(rule.smallestFieldGroup!!.contains("c"))
    }

    @Test
    fun rulesFromString() {
        val before = AnalyzeFactory<String>()
        val rule = FieldRule("global", fields("abc"), 1)
        before.addRule(rule)
        before.addRule(createRule("(b + c) = 1"))
        val root = before.solve()
        assertEquals(0.0, root.getGroupFor("a")!!.probability, EPSILON)
        assertEquals(0.5, root.getGroupFor("b")!!.probability, EPSILON)
        assertEquals(root.getGroupFor("b"), root.getGroupFor("c"))
    }

    private fun fields(string: String): Collection<String> {
        val str: MutableList<String> = ArrayList<String>()
        for (i in 0 until string.length) str.add(string[i].toString())
        assertEquals(string.length, str.size)
        return str
    }

    private fun createRule(string: String): FieldRule<String> {
        val equalSplit = string.split(" = ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        var ruleFields = equalSplit[0]
        ruleFields = ruleFields.substring(1, ruleFields.length - 1)
        val ruleValue = equalSplit[1].toInt()
        val fields = ruleFields.split(" \\+ ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val rule: FieldRule<String> =
            FieldRule<String>("ruleString $string", java.util.Arrays.asList<String>(*fields), ruleValue)
        assertEquals(string, rule.toString())
        return rule
    }

    companion object {
        private const val EPSILON = 0.000000001
    }
}