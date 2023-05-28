package net.zomis.minesweeper.analyze.detail

import net.zomis.minesweeper.analyze.FieldGroup
import net.zomis.minesweeper.analyze.GroupValues

interface ProbabilityKnowledge<T> {
    /**
     * @return The field that this object has stored the probabilities for
     */
    val field: T

    /**
     * @return The [FieldGroup] for the field returned by [.getField]
     */
    val fieldGroup: FieldGroup<T>?

    /**
     * @return How many mines has already been found for this field
     */
    val found: Int

    /**
     * @return The mine probability for the [FieldGroup] returned by [.getFieldGroup]
     */
    val mineProbability: Double

    /**
     * @return [GroupValues] object for what neighbors the field returned by [.getField] has
     */
    val neighbors: GroupValues<T>

    /**
     * @return The array of the probabilities for what number this field has. The sum of this array + the value of [.getMineProbability] will be 1.
     */
    val probabilities: DoubleArray
}