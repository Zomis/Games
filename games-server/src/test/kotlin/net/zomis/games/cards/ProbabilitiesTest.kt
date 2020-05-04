package net.zomis.games.cards

import net.zomis.games.cards.probabilities.CardCounter
import net.zomis.games.cards.probabilities.CardGroup
import net.zomis.games.cards.probabilities.CardSolution
import net.zomis.games.cards.probabilities.CardSolutions
import net.zomis.games.dsl.DslConsoleView
import net.zomis.games.dsl.impl.GameImpl
import net.zomis.games.impl.Hanabi
import net.zomis.games.impl.HanabiCard
import net.zomis.games.impl.HanabiGame
import net.zomis.games.server2.db.DBIntegration
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TestCard(val model: String) {
	override fun toString(): String {
		return model
	}
}
fun <T> outputSolution(solution: CardSolution<CardZone<T>, T>) {
	println("Solution: $solution")
	solution.getAssignments().forEach { (zone, values) -> println("$zone --> $values") }
	println()
}
class ProbabilitiesTest {

	val a = "a"
	val b = "b"
	val c = "c"
	val d = "d"
	val x = CardZone(mutableListOf(TestCard(a), TestCard(a))).also { it.name = "x" }
	val y = CardZone(mutableListOf(TestCard(b), TestCard(c))).also { it.name = "y" }
	val z = CardZone(mutableListOf(TestCard(d), TestCard(d))).also { it.name = "z" }
	val known = CardZone(mutableListOf(TestCard(b), TestCard(c))).also { it.name = "known" }
	private lateinit var counter: CardCounter<TestCard>
	private lateinit var solutions: CardSolutions<CardZone<TestCard>, TestCard>
	private lateinit var groupD: CardGroup<TestCard>
	private lateinit var groupA: CardGroup<TestCard>
	private lateinit var groupBC: CardGroup<TestCard>
	private lateinit var groupBCknown: CardGroup<TestCard>

	@BeforeEach
	fun solveXYZ() {
		// https://github.com/Zomis/ZonesAndCards/blob/master/src/test/java/net/zomis/cards/analyze/ZonesXYZCardsABCDTest.java
        counter = CardCounter<TestCard>()
            .hiddenZones(x, y, z).knownZones(known)
            .exactRule(x, 0) { card -> card.model == "d" }
            .exactRule(y, 0) { card -> card.model == "a" }
		solutions = counter.solve()

		groupD = solutions.getGroups().first { grp -> grp.getCards().any { card -> card.model == d } }
		groupA = solutions.getGroups().first { grp -> grp.getCards().any { card -> card.model == a } }

		val bc: (TestCard) -> Boolean = { card -> card.model == b }
		val knownZone: (TestCard) -> Boolean = { card -> known.cards.contains(card) }
		groupBCknown = solutions.getGroups().first { grp -> grp.getCards().any { bc(it) && knownZone(it) } }
		groupBC = solutions.getGroups().first { grp -> grp.getCards().any { bc(it) && !knownZone(it) } }
    }

	fun printXYZ() {
		val groupD = solutions.getGroups()
		println(solutions)
		println(solutions.getTotalCombinations())
		solutions.getSolutions().forEach { println(it) }

		TODO("Not entirely sure about these probabilities here... Verify them")
	}

	@Test
	fun assertSize() {
		Assertions.assertEquals(6, solutions.getSolutions().size)
	}

	@Test
	fun assertCombinations() {
		val solve = solutions
		val combinations = solve.getSolutions().stream().mapToDouble { it.combinations }.toArray()
		combinations.sort()
		Assertions.assertArrayEquals(arrayOf(1.0, 1.0, 1.0, 4.0, 4.0, 8.0).toDoubleArray(), combinations, 0.00001)
	}

	@Test
	fun assertKnownGroup() {
		solutions.getSolutions().forEach { sol ->
			Assertions.assertEquals(2, sol.getAssignment(known, groupBCknown))
			Assertions.assertEquals(0, sol.getAssignment(known, groupBC))
			Assertions.assertEquals(0, sol.getAssignment(y, groupA))
			Assertions.assertEquals(0, sol.getAssignment(x, groupD))
		}
	}

	@Test
	fun probabilityOfDinY() {
		val solve = solutions
		val prob = solve.getProbabilityDistributionOf(y) { card -> card.model == d }
		println(prob.contentToString())

		Assertions.assertArrayEquals(doubleArrayOf(1.0 / 19.0, 12.0 / 19.0, 6.0 / 19.0), prob, 0.00001)
	}

	@Test
	fun assert_Y1d1bc_Z1d1a_X1bc1a() {
		val solve = solutions
		val solutionss = solve.getSolutions().sortedBy { sol -> sol.combinations }
		val groups = solve.getGroups()
		val dGroup = groups.first { grp -> grp.getCards().any { card -> card.model == d } }


		val y2solutions = solutionss.filter { sol -> (sol.getAssignments()[y] ?: error("")).assigns[dGroup] == 2 }
		Assertions.assertEquals(3, y2solutions.size)
		val solutionXbc = y2solutions.single { sol -> (sol.getAssignments()[x] ?: error("")).assigns[groupBC] == 2 }
//		assertAssignment(solution, dGroup, );
		println("y = 2d, x=bc:")
		outputSolution(solutionXbc)
		Assertions.assertEquals(2, solutionXbc.getAssignment(z, groupA))
		Assertions.assertEquals(2, solutionXbc.getAssignment(x, groupBC))
		Assertions.assertEquals(2, solutionXbc.getAssignment(known, groupBCknown))
		Assertions.assertEquals(2, solutionXbc.getAssignment(y, groupD))

		val solutionXaa = y2solutions.single { sol -> (sol.getAssignments()[x] ?: error("")).assigns[groupA] == 2 }
//		assertAssignment(solution, dGroup, );
		println("y = 2d, x=aa:")
		outputSolution(solutionXaa)
		Assertions.assertEquals(2, solutionXaa.getAssignment(z, groupBC))
		Assertions.assertEquals(2, solutionXaa.getAssignment(x, groupA))
		Assertions.assertEquals(2, solutionXaa.getAssignment(known, groupBCknown))
		Assertions.assertEquals(2, solutionXaa.getAssignment(y, groupD))

		y2solutions.single { sol -> (sol.getAssignments()[x] ?: error("")).assigns[groupA] == 1 }.let {
			println("y = 2d, x=aa:")
			outputSolution(it)
			Assertions.assertEquals(1, it.getAssignment(x, groupA))
			Assertions.assertEquals(1, it.getAssignment(x, groupBC))

			Assertions.assertEquals(2, it.getAssignment(y, groupD))

			Assertions.assertEquals(1, it.getAssignment(z, groupA))
			Assertions.assertEquals(1, it.getAssignment(z, groupBC))

			Assertions.assertEquals(2, it.getAssignment(known, groupBCknown))
		}
//		assertAssignment(solution, dGroup, );

		solutionss.forEach(::outputSolution)
	}

}