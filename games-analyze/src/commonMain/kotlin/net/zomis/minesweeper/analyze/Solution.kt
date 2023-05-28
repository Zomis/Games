package net.zomis.minesweeper.analyze

import kotlin.math.floor
import kotlin.random.Random

/**
 * Represents a solution for a Minesweeper analyze. This has the [FieldGroup]s
 *
 * @author Simon Forsberg
 * @param <T> The field type
</T> */
class Solution<T> private constructor(internal val setGroupValues: GroupValues<T>) {
    private var mapTotal = 0.0
    var combinations = 0.0
        private set

    private fun combination(grpValues: List<Map.Entry<FieldGroup<T>, Int>>, combination: Double): List<T>? {
        if (grpValues.isEmpty()) {
            return emptyList()
        }
        var grpValues = grpValues.toMutableList()
        val first: Map.Entry<FieldGroup<T>, Int> = grpValues.removeAt(0)
        var remaining = 1.0
        for (fr in grpValues) {
            remaining = remaining * nCr(fr)
        }
        val fncr = nCr(first)
        require(combination < remaining * fncr) { "Not enough combinations. " + combination + " max is " + remaining * fncr }
        val combo = combination % fncr
        val list = Combinatorics.listCombination(combo, first.value, first.key.fields)
        if (grpValues.isNotEmpty()) {
            val recursive = combination(grpValues, floor(combination / fncr)) ?: return null
            list!!.addAll(recursive)
        }
        return list
    }

    fun copyWithoutNCRData(): Solution<T> {
        return Solution(setGroupValues)
    }

    fun getCombination(combinationIndex: Double): List<T>? {
        return combination(setGroupValues.entrySet().toList(), combinationIndex)
    }

    val probability: Double
        get() {
            check(mapTotal != 0.0) { "The total number of solutions on map is unknown" }
            return combinations / mapTotal
        }

    @Deprecated("")
    fun getRandomSolution(random: Random): List<T> {
        val result: MutableList<T> = mutableListOf()
        for (ee in setGroupValues.entrySet()) {
            val pickable = ee.key.fields.toMutableList()
            for (i in 0 until ee.value) {
                val nextPick: Int = random.nextInt(pickable.size)
                result.add(pickable.removeAt(nextPick))
            }
        }
        return result
    }

    fun getSetGroupValues(): GroupValues<T> {
        return GroupValues(setGroupValues)
    }

    fun nCr(): Double {
        return combinations
    }

    private fun nCrPerform(): Solution<T> {
        var result = 1.0
        for (ee in setGroupValues.entrySet()) {
            result = result * Combinatorics.nCr(ee!!.key.size, ee.value!!)
        }
        combinations = result
        return this
    }

    fun setTotal(total: Double) {
        mapTotal = total
        for (ee in setGroupValues.entrySet()) {
            ee!!.key!!.informAboutSolution(ee.value!!, this, total)
        }
    }

    override fun toString(): String {
        val str = StringBuilder()
        for (ee in setGroupValues.entrySet()) {
            str.append(ee!!.key.toString() + " = " + ee.value + ", ")
        }
        str.append(combinations.toString() + " combinations (" + probability + ")")
        return str.toString()
    }

    companion object {
        fun <T> createSolution(values: GroupValues<T>): Solution<T> {
            return Solution(values).nCrPerform()
        }

        private fun <T> nCr(rule: Map.Entry<FieldGroup<T>, Int>): Double {
            return Combinatorics.nCr(rule.key.size, rule.value)
        }
    }
}