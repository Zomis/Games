package net.zomis.minesweeper.analyze.detail

import net.zomis.minesweeper.analyze.*
import kotlin.math.min

class FieldProxy<T> : ProbabilityKnowledge<T> {
    private val interruptCheck: InterruptCheck
    private lateinit var detailedCombinations: DoubleArray
    override lateinit var probabilities: DoubleArray
        private set
    override val field: T
    override var found: Int
        private set
    override val fieldGroup: FieldGroup<T>
    override val neighbors: GroupValues<T>

    @Deprecated("")
    constructor(group: FieldGroup<T>, field: T) {
        interruptCheck = NoInterrupt()
        this.field = field
        neighbors = GroupValues()
        fieldGroup = group
        found = 0
    }

    internal constructor(interruptCheck: InterruptCheck, group: FieldGroup<T>, field: T) {
        this.interruptCheck = interruptCheck
        this.field = field
        neighbors = GroupValues()
        fieldGroup = group
        found = 0
    }

    fun addSolution(solution: Solution<T>) {
        recursiveRemove(
            solution.copyWithoutNCRData().setGroupValues.entrySet().toList(),
            1.0,
            0,
            0
        )
    }

    /**
     * This field has the same values as another field, copy the values.
     *
     * @param copyFrom [FieldProxy] to copy from
     * @param analyzeTotal Total number of combinations
     */
    fun copyFromOther(copyFrom: FieldProxy<T>, analyzeTotal: Double) {
        val length = min(
            detailedCombinations.size - found,
            copyFrom.detailedCombinations.size - copyFrom.found
        )
        copyFrom.detailedCombinations.copyInto(detailedCombinations, found, copyFrom.found, copyFrom.found + length)
        finalCalculation(analyzeTotal)
    }

    /**
     * Calculate final probabilities from the combinations information
     *
     * @param analyzeTotal Total number of combinations
     */
    fun finalCalculation(analyzeTotal: Double) {
        probabilities = DoubleArray(detailedCombinations.size)
        for (i in probabilities.indices) {
            probabilities[i] = detailedCombinations[i] / analyzeTotal
        }
    }

    /**
     * Setup the neighbors for this field
     *
     * @param neighborStrategy [NeighborFind] strategy
     * @param proxyProvider Interface to get the related proxies
     */
    fun fixNeighbors(neighborStrategy: NeighborFind<T>, proxyProvider: ProxyProvider<T>) {
        val realNeighbors = neighborStrategy.getNeighborsFor(field)
        detailedCombinations = DoubleArray(realNeighbors!!.size + 1)
        for (neighbor in realNeighbors) {
            if (neighborStrategy.isFoundAndisMine(neighbor)) {
                found++
                continue  // A found mine is not, and should not be, in a fieldproxy
            }
            val proxy = proxyProvider.getProxyFor(neighbor) ?: continue
            val neighborGroup = proxy.fieldGroup
            if (neighborGroup != null) {
                // Ignore zero-probability neighborGroups
                if (neighborGroup.probability == 0.0) {
                    continue
                }

                // Increase the number of neighbors
                val currentNeighborAmount = neighbors[neighborGroup]
                if (currentNeighborAmount == null) {
                    neighbors.put(neighborGroup, 1)
                } else neighbors.put(neighborGroup, currentNeighborAmount + 1)
            }
        }
    }

    override val mineProbability: Double
        get() = fieldGroup.probability

    private fun recursiveRemove(
        solution: List<Map.Entry<FieldGroup<T>, Int>>, combinations: Double,
        mines: Int, listIndex: Int
    ) {
        if (interruptCheck.isInterrupted) {
            throw RuntimeTimeoutException()
        }

        // Check if there are more field groups with values
        if (listIndex >= solution.size) {
            // TODO: or if combinations equals zero ?
            detailedCombinations[mines + found] += combinations
            return
        }

        // Get the assignment
        val (group, n) = solution[listIndex]

        // Setup values for the hypergeometric distribution calculation. See http://en.wikipedia.org/wiki/Hypergeometric_distribution
        var N = group.size
        val K = neighbors[group]
        if (fieldGroup === group) {
            N-- // Always exclude self becuase you can't be neighbor to yourself
        }
        if (K == null) {
            // This field does not have any neighbors to that group.
            recursiveRemove(solution, combinations * Combinatorics.nCr(N, n), mines, listIndex + 1)
            return
        }

        // Calculate the values and then calculate for the next group
        val maxLoop: Int = min(K, n)
        for (k in minK(N, K, n)..maxLoop) {
            val thisCombinations = Combinatorics.NNKK(N, n, K, k)
            recursiveRemove(solution, combinations * thisCombinations, mines + k, listIndex + 1)
        }
    }

    override fun toString(): String {
        return """Proxy(${field.toString()})
 neighbors: ${neighbors}
 group: ${fieldGroup.toString()}
 Mine prob ${fieldGroup.probability} Numbers: ${probabilities.contentToString()}"""
    }

    companion object {
        private fun minK(N: Int, K: Int, n: Int): Int {
            // If all fields in group are neighbors to this field then all mines must be neighbors to this field as well
            return if (N == K) n else 0
        }
    }
}