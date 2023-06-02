package net.zomis.minesweeper.analyze

import net.zomis.minesweeper.analyze.listener.SolveListener
import kotlin.jvm.JvmOverloads

/**
 * Class for creating [AnalyzeResult]s
 *
 * @author Simon Forsberg
 *
 * @param <T> The type of field to do analyze on
</T> */
open class AnalyzeFactory<T> {
    private val rules: MutableList<RuleConstraint<T>> = mutableListOf()
    private val interruptCheck: InterruptCheck
    var listener: SolveListener<T>? = null
        private set

    internal constructor(interruptCheck: InterruptCheck, known: Solution<T>, rules: List<RuleConstraint<T>>) {
        this.interruptCheck = interruptCheck
        for (sol in known.setGroupValues.entrySet()) {
            this.rules.add(FieldRule(null, sol!!.key, sol.value!!))
        }
        this.rules.addAll(rules!!)
    }
    /**
     * Create a new, empty analyze factory using a specific Interrupt condition
     */
    /**
     * Create a new, empty analyze factory
     */
    @JvmOverloads
    constructor(interruptCheck: InterruptCheck = NoInterrupt()) {
        this.interruptCheck = interruptCheck
    }

    /**
     * Solve this analyze
     *
     * @return An [AnalyzeResult] object for the result of the analyze.
     */
    fun solve(): AnalyzeResult<T> {
        val original: MutableList<RuleConstraint<T>> =
            ArrayList<RuleConstraint<T>>(rules.size)
        for (rule in rules) {
            original.add(rule!!.copy())
        }
        val inProgress: MutableList<RuleConstraint<T>> =
            ArrayList<RuleConstraint<T>>(rules.size)
        for (rule in rules) {
            inProgress.add(rule!!.copy())
        }
        val solutions: MutableList<Solution<T>> = mutableListOf()
//        splitFieldRules<T>(inProgress)
        FieldGroupSplit.superSplit(inProgress)
        inProgress.flatMap { it.fieldGroups() }.sanityCheck()

        val solveListener: SolveListener<T> =
            if (listener != null) listener!! else SolveListener { analyze, group, value ->
                // no operation
            }
        val analyze = GameAnalyze(interruptCheck, null, inProgress, 0, solveListener)
        val total = analyze.solve(solutions)
        for (solution in solutions) {
            solution!!.setTotal(total)
        }
        val groups: MutableList<FieldGroup<T>> = mutableListOf<FieldGroup<T>>()
        if (solutions.isNotEmpty()) {
            for (group in solutions[0]!!.setGroupValues.keySet()) {
                // All solutions should contain the same fieldgroups.
                groups.add(group)
            }
        }
        return AnalyzeResultsImpl(original, inProgress, groups, solutions, total)
    }

    /**
     * Add a new rule constraint that needs to be respected in all solutions
     *
     * @param rule [FieldRule] to add
     */
    fun addRule(rule: RuleConstraint<T>): AnalyzeFactory<T> {
        rules.add(rule)
        return this
    }

    fun setListener(listener: SolveListener<T>?): AnalyzeFactory<T> {
        this.listener = listener
        return this
    }

    /**
     * Get the rules that has been added to this analyze
     *
     * @return List of [FieldRule]s that has been added
     */
    fun getRules(): List<RuleConstraint<T>> {
        return rules.toList()
    }

    companion object {
        /**
         * Separate fields into field groups. Example `a + b + c = 2` and `b + c + d = 1` becomes `(a) + (b + c) = 2` and `(b + c) + (d) = 1`. This method is called automatically when calling [.solve]
         * @param rules List of rules to split
         */
        /**
         * Split the current field rules that has been added to this object
         */
        fun <T> splitFieldRules(rules: List<RuleConstraint<T>>) {
            if (rules.size <= 1) {
                return
            }
            do {
                var splitPerformed = false
                for (a in rules) {
                    for (b in rules) {
                        splitPerformed = splitPerformed or checkIntersection(a, b)
                    }
                }
            } while (splitPerformed)
        }

        private fun <T> checkIntersection(ruleA: RuleConstraint<T>, ruleB: RuleConstraint<T>): Boolean {
            if (ruleA === ruleB) {
                return false
            }
            val fieldsA = ruleA.fieldGroups()
            val fieldsB = ruleB.fieldGroups()
            val fieldsCopy: List<FieldGroup<T>> = ArrayList<FieldGroup<T>>(ruleA.fieldGroups())
            val ruleFieldsCopy: List<FieldGroup<T>> = ArrayList<FieldGroup<T>>(ruleB.fieldGroups())
            for (groupA in fieldsCopy) {
                for (groupB in ruleFieldsCopy) {
                    if (groupA == groupB) {
                        continue
                    }
                    val splitResult: FieldGroupSplit<T> = FieldGroupSplit.Companion.split<T>(groupA, groupB)
                        ?: continue  // nothing to split
                    val both = splitResult.both
                    val onlyA = splitResult.onlyA
                    val onlyB = splitResult.onlyB
                    fieldsA.remove(groupA)
                    fieldsA.add(both)
                    if (onlyA.isNotEmpty()) {
                        fieldsA.add(onlyA)
                    }
                    fieldsB.remove(groupB)
                    fieldsB.add(both)
                    if (onlyB.isNotEmpty()) {
                        fieldsB.add(onlyB)
                    }
                    return true
                }
            }
            return false
        }
    }
}