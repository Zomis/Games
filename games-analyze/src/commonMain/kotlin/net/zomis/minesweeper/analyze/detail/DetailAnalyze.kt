package net.zomis.minesweeper.analyze.detail

import net.zomis.minesweeper.analyze.AnalyzeResult
import net.zomis.minesweeper.analyze.GroupValues
import net.zomis.minesweeper.analyze.InterruptCheck
import net.zomis.minesweeper.analyze.NoInterrupt

/**
 * Creator of [DetailedResults] given an [AnalyzeResult] and a [NeighborFind] strategy
 *
 * @author Simon Forsberg
 */
object DetailAnalyze {
    @Deprecated("")
    fun <T> solveDetailed(analyze: AnalyzeResult<T>, neighborStrategy: NeighborFind<T>): DetailedResults<T> {
        return solveDetailed<T>(NoInterrupt(), analyze, neighborStrategy)
    }

    fun <T> solveDetailed(
        interruptCheck: InterruptCheck,
        analyze: AnalyzeResult<T>, neighborStrategy: NeighborFind<T>
    ): DetailedResults<T> {
        // Initialize FieldProxies
        val proxies: MutableMap<T, FieldProxy<T>> = mutableMapOf()
        for (group in analyze.groups) {
            for (field in group) {
                val proxy = FieldProxy(interruptCheck, group, field)
                proxies[field] = proxy
            }
        }

        // Setup proxy provider
        val provider = ProxyProvider<T> { field -> proxies[field] }

        // Setup neighbors for proxies
        for (fieldProxy in proxies.values) {
            fieldProxy.fixNeighbors(neighborStrategy!!, provider)
        }
        val totalCombinations = analyze.total
        val bufferedValues: MutableMap<GroupValues<T>, FieldProxy<T>> =
            HashMap<GroupValues<T>, FieldProxy<T>>()
        for (proxy in proxies.values) {
            // Check if it is possible to re-use a previous value
            val bufferedValue = bufferedValues[proxy.neighbors]
            if (bufferedValue != null && bufferedValue.fieldGroup === proxy.fieldGroup) {
                proxy.copyFromOther(bufferedValue, totalCombinations)
                continue
            }

            // Setup the probabilities for this field proxy
            for (solution in analyze.solutionIteration) {
                proxy.addSolution(solution!!)
            }
            proxy.finalCalculation(totalCombinations)
            bufferedValues[proxy.neighbors] = proxy
        }
        val proxyCount = bufferedValues.size
        return DetailedResultsImpl(analyze, proxies, proxyCount)
    }
}