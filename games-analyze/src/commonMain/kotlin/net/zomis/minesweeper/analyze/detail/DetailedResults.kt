package net.zomis.minesweeper.analyze.detail

import net.zomis.minesweeper.analyze.AnalyzeResult

/**
 * Interface for retreiving more detailed probabilities, for example 'What is the probability for a 4 on field x?'
 *
 * @author Simon Forsberg
 *
 * @param <T> The field type
</T> */
interface DetailedResults<T> {
    fun allProxies(): Collection<ProbabilityKnowledge<T>>

    /**
     * Get the number of unique proxies that was required for the calculation. As some can be re-used, this will always be lesser than or equal to `getProxyMap().size()`
     *
     * @return The number of unique proxies
     */
    val proxyCount: Int

    /**
     * Get the detailed probabilities for a field
     *
     * @param field The field to get the information for
     * @return An object containing detailed probability information for the chosen field
     */
    fun getProxyFor(field: T): ProbabilityKnowledge<T>

    /**
     * Get the underlying analyze that these detailed results was based on
     *
     * @return [AnalyzeResult] object that is the source of this analyze
     */
    val analyze: AnalyzeResult<T>

    /**
     * @return The map of all probability datas
     */
    val proxyMap: Map<T, ProbabilityKnowledge<T>>
}