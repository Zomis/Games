package net.zomis.minesweeper.analyze

import net.zomis.minesweeper.analyze.detail.DetailedResults
import net.zomis.minesweeper.analyze.detail.NeighborFind
import kotlin.random.Random

interface AnalyzeResult<T> {
    fun cloneAddSolve(extraRules: List<RuleConstraint<T>>): AnalyzeResult<T>
    val fields: List<T>
    fun getGroupFor(field: T): FieldGroup<T>?
    val groups: List<FieldGroup<T>>
    val originalRules: List<RuleConstraint<T>>
    fun getProbabilityOf(extraRules: List<RuleConstraint<T>>): Double
    val rules: List<RuleConstraint<T>>
    fun getSolution(solution: Double): List<T>?
    val solutionIteration: Iterable<Solution<T>>
    val solutions: List<Solution<T>>

    /**
     * Get the total number of combinations of Field placements.
     * @return The number of combinations for the analyze. 0 if the analyze is impossible.
     */
    val total: Double
    fun randomSolution(random: Random): List<T>?
    fun analyzeDetailed(neighborStrategy: NeighborFind<T>): DetailedResults<T>
}