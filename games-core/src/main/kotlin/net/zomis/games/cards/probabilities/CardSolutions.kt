package net.zomis.games.cards.probabilities

import net.zomis.games.cards.CardZone

class CardSolutions<Z : CardZone<*>, C>(private val solutions: List<CardSolution<Z, C>>) {

    fun getProbabilityDistributionOf(zone: Z, predicate: (C) -> Boolean): DoubleArray {
        val dbl = DoubleArray(zone.size + 1)

        for (sol in solutions) {
            val result = sol.getProbabilityDistributionOf(zone, predicate)
            result.indices.forEach {
                dbl[it] += result[it]
            }
        }

        val total = getTotalCombinations()
        for (i in dbl.indices) {
            dbl[i] = dbl[i] / total
        }
        return dbl
    }

    fun getSolutions(): List<CardSolution<Z, C>> = solutions.toList()

    fun getTotalCombinations(): Double = this.solutions.sumByDouble { it.combinations }

    /**
     *
     * @return A list of all the {@link CardGroup}s available in this solution set.
     */
    fun getGroups(): List<CardGroup<C>> {
        val firstSolution = solutions[0]
        val assignments = firstSolution.getAssignments()
        return assignments.values.iterator().next().groups.toList()
    }

    fun getSolutionsWithAssignment(zone: Z, group: CardGroup<C>, value: Int): CardSolutions<Z, C> {
        val solutions = mutableListOf<CardSolution<Z, C>>()

        for (sol in this.solutions) {
            if (sol.getAssignment(zone, group) == value) {
                solutions.add(sol)
            }
        }

        return CardSolutions(solutions)
    }

}