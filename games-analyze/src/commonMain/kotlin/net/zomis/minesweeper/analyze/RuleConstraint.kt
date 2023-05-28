package net.zomis.minesweeper.analyze

import net.zomis.minesweeper.analyze.listener.RuleListener

interface RuleConstraint<T> {
    /**
     * Apply various values to this rule to potentially simplify it and learn something new
     *
     * @param knownValues Known values that can be removed and cleaned up from this rule to simplify it
     * @param listener
     * @return A [SimplifyResult] corresponding to how successful the simplification was
     */
    fun simplify(knownValues: GroupValues<T>, listener: RuleListener<T>): SimplifyResult

    /**
     * Create a copy of this rule, for trial-and-error purposes
     *
     * @return A copy of this rule in its current state
     */
    fun copy(): RuleConstraint<T>

    /**
     * Determine whether or not this rule is finished and thus can be removed from the list of rules
     *
     * @return True if this rule is successfully finished, false otherwise
     */
    val isEmpty: Boolean

    /**
     * Find the best field group to branch on
     *
     * @return The best [FieldGroup] to branch on, or null if this rule does not have a preference about how to branch
     */
    val smallestFieldGroup: FieldGroup<T>?

    /**
     * @return An indication on what caused this rule to be created
     */
    val cause: T?

    /**
     * @return Direct access to the [FieldGroup]s in this rule, in order to split them
     */
    fun fieldGroups(): MutableList<FieldGroup<T>>
}