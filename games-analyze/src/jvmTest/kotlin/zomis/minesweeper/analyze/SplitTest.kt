package zomis.minesweeper.analyze

import net.zomis.minesweeper.analyze.AnalyzeFactory
import net.zomis.minesweeper.analyze.FieldRule
import net.zomis.minesweeper.analyze.RuleConstraint
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class SplitTest {

    @Test
    fun test() {
        val rules = mutableListOf<RuleConstraint<String>>()
        rules.add(FieldRule(null, listOf("a", "b", "c"), 2))
        rules.add(FieldRule(null, listOf("b", "c", "d", "e"), 2))
        AnalyzeFactory.splitFieldRules(rules)
        println(rules)

        val rule1groups = rules[0].fieldGroups()
        val rule2groups = rules[1].fieldGroups()
        Assertions.assertEquals(2, rule1groups.size)
        Assertions.assertEquals(listOf("a"), rule1groups[1].fields)
        Assertions.assertEquals(listOf("b", "c"), rule1groups[0].fields)

        Assertions.assertEquals(2, rule2groups.size)
        Assertions.assertEquals(listOf("b", "c"), rule2groups[0].fields)
        Assertions.assertEquals(listOf("d", "e"), rule2groups[1].fields)
    }

    @Test
    fun testWithSameChars() {
        val rules = mutableListOf<RuleConstraint<String>>()
        rules.add(FieldRule(null, listOf("a", "b", "b"), 2))
        rules.add(FieldRule(null, listOf("b", "b", "c", "c"), 2))
        AnalyzeFactory.splitFieldRules(rules)
        println(rules)

        val rule1groups = rules[0].fieldGroups()
        val rule2groups = rules[1].fieldGroups()
        Assertions.assertEquals(2, rule1groups.size)
        Assertions.assertEquals(listOf("a"), rule1groups[1].fields)
        Assertions.assertEquals(listOf("b", "b"), rule1groups[0].fields)

        Assertions.assertEquals(2, rule2groups.size)
        Assertions.assertEquals(listOf("b", "b"), rule2groups[0].fields)
        Assertions.assertEquals(listOf("c", "c"), rule2groups[1].fields)
    }

}