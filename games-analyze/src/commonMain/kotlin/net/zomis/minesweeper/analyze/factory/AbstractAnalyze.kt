package net.zomis.minesweeper.analyze.factory

import net.zomis.minesweeper.analyze.AnalyzeFactory
import net.zomis.minesweeper.analyze.FieldRule
import net.zomis.minesweeper.analyze.detail.NeighborFind

abstract class AbstractAnalyze<F> : AnalyzeFactory<F>(), NeighborFind<F> {
    protected abstract val allPoints: List<F>
    protected fun createRules(points: List<F>) {
        val knownNonMines: MutableSet<F> = mutableSetOf()
        val remaining = remainingMinesCount
        if (remaining != -1) {
            addRule(FieldRule(null, allUnclickedFields, remaining))
        }
        for (field in points) {
            if (!fieldHasRule(field)) continue
            val newRule = internalRuleFromField(field, knownNonMines)
            if (newRule != null) {
                addRule(newRule)
            }
        }
        if (knownNonMines.isNotEmpty()) addRule(FieldRule(null, knownNonMines, 0))
    }

    /**
     * Determines if the specified field is/has a rule that should be added to the constraints
     *
     * @param field Field that is being checked
     * @return True if the field has a rule that should be applied, false otherwise
     */
    protected abstract fun fieldHasRule(field: F): Boolean
    protected abstract val remainingMinesCount: Int
    protected abstract val allUnclickedFields: List<F>
    private fun internalRuleFromField(field: F, knownNonMines: MutableSet<F>): FieldRule<F>? {
        val ruleParams: MutableList<F> = mutableListOf()
        var foundNeighbors = 0
        val fieldValue = getFieldValue(field)
        for (neighbor in getNeighbors(field)) {
            if (isDiscoveredMine(neighbor)) foundNeighbors++ else if (!isClicked(neighbor)) ruleParams.add(neighbor)
        }
        return if (fieldValue - foundNeighbors == 0) {
            for (mf in ruleParams) {
                knownNonMines.add(mf)
            }
            null
        } else FieldRule(field, ruleParams, fieldValue - foundNeighbors)
    }

    protected abstract fun isDiscoveredMine(field: F): Boolean
    protected abstract fun getFieldValue(field: F): Int
    protected abstract fun getNeighbors(field: F): List<F>
    protected abstract fun isClicked(field: F): Boolean
    override fun getNeighborsFor(field: F): Collection<F> {
        return getNeighbors(field)
    }

    override fun isFoundAndisMine(field: F): Boolean {
        return isDiscoveredMine(field)
    }

    fun createRules() {
        createRules(allPoints)
    }
}