package net.zomis.minesweeper.ais.utils

import net.zomis.UtilZomisUtils

class OpenField private constructor() {
    private val openFields: MutableCollection<Flags.Field> = ArrayList<Flags.Field>()
    private val neighbors: MutableCollection<Flags.Field> = ArrayList<Flags.Field>()
    val openFieldSource: Flags.Field
        get() = ArrayList<Flags.Field>(openFields).get(0) // Don't use random to always return the same.

    fun neighbors: List<Flags.Field> {
        return ArrayList<Flags.Field>(neighbors)
    }

    val isEven: Boolean
        get() = neighbors.size % 2 == 0

    fun hasField(field: Flags.Field): Boolean {
        return openFields.contains(field) || neighbors.contains(field)
    }

    private fun recursiveAdd(analyze: AnalyzeProvider, field: Flags.Field) {
        if (hasField(field)) return
        val know: ProbabilityKnowledge<Flags.Field> = analyze.getKnowledgeFor(field)
        if (know.probabilities.get(0) == 1.0) {
            openFields.add(field)
            for (neighbor in field.neighbors) {
                if (!neighbor.clicked) {
                    recursiveAdd(analyze, neighbor)
                }
            }
        } else {
            val i: Int = MineprobHelper.getCertainValue(know)
            if (i != null) {
                neighbors.add(field)
            }
        }
    }

    override fun toString(): String {
        return "[OF: open: " + UtilZomisUtils.implode(", ", openFields) + ", neighbors: " + UtilZomisUtils.implode(
            ", ",
            neighbors
        ) + "]"
    }

    companion object {
        fun construct(analyze: AnalyzeProvider?, know: ProbabilityKnowledge<Flags.Field>): OpenField {
            require(know.probabilities.get(0) == 1.0)
            val of = OpenField()
            of.recursiveAdd(analyze, know.field)
            return of
        }
    }
}