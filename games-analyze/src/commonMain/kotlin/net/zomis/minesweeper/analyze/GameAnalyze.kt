package net.zomis.minesweeper.analyze

import net.zomis.minesweeper.analyze.listener.Analyze
import net.zomis.minesweeper.analyze.listener.RuleListener
import net.zomis.minesweeper.analyze.listener.SolveListener

class GameAnalyze<T> internal constructor(
    private val interruptCheck: InterruptCheck,
    knownValues: GroupValues<T>?,
    private val rules: MutableList<RuleConstraint<T>>,
    override val depth: Int,
    private val listener: SolveListener<T>
) : Analyze<T> {
    override val knownValues: GroupValues<T>

    init {
        this.knownValues = knownValues?.let { GroupValues(it) } ?: GroupValues()
    }

    private fun removeEmptyRules() {
        val it = rules.iterator()
        while (it.hasNext()) {
            if (it.next().isEmpty) it.remove()
        }
    }

    private fun simplifyRules(): Boolean {
        var simplifyPerformed = true
        val addedRules: MutableList<RuleConstraint<T>> = mutableListOf()
        val analyze: Analyze<T> = object : Analyze<T> {
            override val depth: Int
                get() = this@GameAnalyze.depth

            override fun addRule(rule: RuleConstraint<T>) {
                addedRules.add(rule)
            }

            override val knownValues: GroupValues<T>
                get() = this@GameAnalyze.knownValuesCopy()
        }
        val ruleListener = RuleListener { group, value -> listener.onValueSet(analyze, group, value) }
        while (simplifyPerformed) {
            simplifyPerformed = false
            val it = rules.iterator()
            while (it.hasNext()) {
                val ruleSimplify = it.next()
                val simplifyResult = ruleSimplify!!.simplify(knownValues, ruleListener)
                if (simplifyResult == SimplifyResult.SIMPLIFIED) {
                    simplifyPerformed = true
                } else if (simplifyResult!!.isFailure) {
                    return false
                }
                if (ruleSimplify.isEmpty) {
                    it.remove()
                }
            }
            if (!addedRules.isEmpty()) {
                rules.addAll(addedRules)
                // as rules have been added, we need to split into field groups again
                AnalyzeFactory.Companion.splitFieldRules<T>(rules)
                addedRules.clear()
                simplifyPerformed = true
            }
        }
        return true
    }

    fun solve(solutions: MutableList<Solution<T>>): Double {
        if (!simplifyRules()) {
            return 0.0
        }
        removeEmptyRules()
        var total = solveRules(solutions)
        if (rules.isEmpty()) {
            val solved: Solution<T> = Solution.createSolution<T>(knownValues)
            solutions.add(solved)
            total += solved.nCr()
        }
        return total
    }

    private fun solveRules(solutions: MutableList<Solution<T>>): Double {
        if (interruptCheck.isInterrupted) {
            throw RuntimeTimeoutException()
        }
        if (rules.isEmpty()) {
            return 0.0
        }
        val chosenGroup = smallestFieldGroup ?: throw IllegalStateException("Chosen group is null: " + rules)
        val groupSize = chosenGroup.size
        check(groupSize != 0) { "Chosen group is empty. $chosenGroup" }
        var total = 0.0
        for (i in 0..groupSize) {
            val mapCopy = GroupValues(knownValues)
            mapCopy.put(chosenGroup, i)
            val rulesCopy: MutableList<RuleConstraint<T>> = mutableListOf() // deep copy!
            for (rule in rules) {
                rulesCopy.add(rule!!.copy())
            }
            val copy = GameAnalyze(interruptCheck, mapCopy, rulesCopy, depth + 1, listener)
            val rulesCountBefore = copy.rules.size
            listener.onValueSet(copy, chosenGroup, i)
            val rulesCountAfter = copy.rules.size
            if (rulesCountBefore != rulesCountAfter) {
                // onValueSet has added rules
                AnalyzeFactory.splitFieldRules<T>(copy.rules)
            }
            total += copy.solve(solutions)
        }
        return total
    }

    private val smallestFieldGroup: FieldGroup<T>?
        private get() {
            for (rule in rules) {
                // TODO: this implementation seem to rely on a small field group existing in the first rule,
                // this is technically not necessary, but is how I have implemented it in Minesweeper Flags Extreme
                val smallest = rule.smallestFieldGroup
                if (smallest != null) {
                    return smallest
                }
            }
            return null
        }

    fun knownValuesCopy(): GroupValues<T> {
        return GroupValues(knownValues)
    }

    override fun addRule(rule: RuleConstraint<T>) {
        rules.add(rule)
    }
}