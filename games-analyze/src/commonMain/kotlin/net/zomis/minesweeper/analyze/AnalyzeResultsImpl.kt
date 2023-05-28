package net.zomis.minesweeper.analyze

import net.zomis.minesweeper.analyze.detail.DetailAnalyze
import net.zomis.minesweeper.analyze.detail.DetailedResults
import net.zomis.minesweeper.analyze.detail.NeighborFind
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.random.Random

class AnalyzeResultsImpl<T>(
    override val originalRules: List<RuleConstraint<T>>,
    override val rules: List<RuleConstraint<T>>,
    override val groups: List<FieldGroup<T>>,
    override val solutions: List<Solution<T>>,
    override val total: Double,
) : AnalyzeResult<T> {

    override fun getGroupFor(field: T): FieldGroup<T>? {
        for (group in groups) {
            if (group.contains(field)) {
                return group
            }
        }
        return null
    }

    /**
     * Return a random solution that satisfies all the rules
     *
     * @param random Random object to perform the randomization
     * @return A list of fields randomly selected that is guaranteed to be a solution to the constraints
     */
    override fun randomSolution(random: Random): List<T> {
        requireNotNull(random) { "Random object cannot be null" }
        val solutions = ArrayList(solutions)
        check(total != 0.0) { "Analyze has 0 combinations: $this" }
        var rand: Double = random.nextDouble() * total
        var theSolution: Solution<T>? = null
        while (rand > 0) {
            check(!solutions.isEmpty()) { "Solutions is suddenly empty. (This should not happen)" }
            theSolution = solutions[0]
            rand -= theSolution.nCr()
            solutions.removeAt(0)
        }
        return theSolution!!.getRandomSolution(random)
    }

    private fun solutionToNewAnalyze(
        solution: Solution<T>,
        extraRules: List<RuleConstraint<T>>
    ): AnalyzeFactory<T> {
        val newRules: MutableList<RuleConstraint<T>> = mutableListOf()
        for (rule in extraRules) {
            // Create new rules, because the older ones may have been simplified already.
            newRules.add(rule.copy())
        }
        return AnalyzeFactory(NoInterrupt(), solution, newRules)
    }

    override fun cloneAddSolve(extraRules: List<RuleConstraint<T>>): AnalyzeResult<T> {
        val newRules = filteredOriginalRules().toMutableList()
        newRules.addAll(extraRules)
        val copy = AnalyzeFactory<T>()
        for (rule in newRules) {
            copy.addRule(rule.copy())
        }
        return copy.solve()
    }

    /**
     * Get the list of the original, non-simplified, rules
     *
     * @return The original rule list
     */
    fun filteredOriginalRules(): List<RuleConstraint<T>> {
        return originalRules.ifEmpty { rules }
    }

    private fun getTotalWith(extraRules: List<RuleConstraint<T>>): Double {
        var total = 0.0
        for (solution in solutions) {
            val root = solutionToNewAnalyze(solution, extraRules).solve()
            total += root.total
        }
        return total
    }

    override fun getProbabilityOf(extraRules: List<RuleConstraint<T>>): Double {
        return getTotalWith(extraRules) / total
    }

    fun filteredGroups(): List<FieldGroup<T>> {
        return groups.filter { !it.isEmpty }
    }

    override val fields: List<T>
        get() {
            val allFields: MutableList<T> = ArrayList<T>()
            for (group in filteredGroups()) {
                allFields.addAll(group)
            }
            return allFields
        }

    override fun getSolution(solution: Double): List<T>? {
        var solution = solution
        require(!(floor(solution) != ceil(solution) || solution < 0 || solution >= total)) { "solution must be an integer between 0 and total (" + total + ")" }
        check(!solutions.isEmpty()) { "There are no solutions." }
        val solutions = ArrayList<Solution<T>>(
            solutions
        )
        var theSolution = solutions[0]
        while (solution > theSolution.nCr()) {
            solution -= theSolution.nCr()
            solutions.removeAt(0)
            theSolution = solutions[0]
        }
        return theSolution.getCombination(solution)
    }

    override val solutionIteration: Iterable<Solution<T>> get() = solutions

    override fun analyzeDetailed(neighborStrategy: NeighborFind<T>): DetailedResults<T> {
        return DetailAnalyze.solveDetailed(this, neighborStrategy)
    }
}