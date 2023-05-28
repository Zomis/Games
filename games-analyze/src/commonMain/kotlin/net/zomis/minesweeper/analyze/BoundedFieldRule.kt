package net.zomis.minesweeper.analyze

import net.zomis.minesweeper.analyze.listener.RuleListener

/**
 * A constraint of a number of fields or [FieldGroup]s that should have a sum within a specific range
 *
 * @author Simon Forsberg
 * @param <T> Field type
</T> */
open class BoundedFieldRule<T> : RuleConstraint<T> {
    final override val cause: T?
    protected val fields: MutableList<FieldGroup<T>>
    var maxResult = 0
        protected set
    var minResult = 0
        protected set

    /**
     * Create a copy of an existing rule.
     *
     * @param copyFrom Rule to copy
     */
    private constructor(copyFrom: BoundedFieldRule<T>) {
        cause = copyFrom.cause
        fields = copyFrom.fields.toMutableList()
        minResult = copyFrom.minResult
        maxResult = copyFrom.maxResult
    }

    /**
     * Create a rule from a list of fields and a result (create a new FieldGroup for it)
     *
     * @param cause The reason for why this rule is added (optional, may be null)
     * @param rule Fields that this rule applies to
     * @param min The minimum value that should be forced for the fields
     * @param max The maximum value that should be forced for the fields
     */
    constructor(cause: T?, rule: Collection<T>, min: Int, max: Int) {
        this.cause = cause
        fields = mutableListOf()
        fields.add(FieldGroup(rule))
        minResult = min
        maxResult = max
    }

    constructor(cause: T?, fields: List<FieldGroup<T>>, min: Int, max: Int) {
        this.cause = cause
        this.fields = fields.toMutableList()
        minResult = min
        maxResult = max
    }

    override val isEmpty: Boolean
        get() = fields.isEmpty() && minResult <= 0 && maxResult >= 0

    override fun simplify(knownValues: GroupValues<T>, listener: RuleListener<T>): SimplifyResult {
        if (isEmpty) {
            return SimplifyResult.NO_EFFECT
        }
        val it = fields.iterator()
        // a + b <= 1 ---- a = 1 ---> b <= 0 ---> b = 0
        var totalCount = 0
        while (it.hasNext()) {
            val group = it.next()
            val known = knownValues[group]
            if (known != null) {
                it.remove()
                minResult -= known
                maxResult -= known
            } else totalCount += group.size
        }

        // a + b < 0 is not a valid rule
        if (maxResult < 0) {
            return SimplifyResult.FAILED_NEGATIVE_RESULT
        }

        // a + b > 2 is not a valid rule.
        if (minResult > totalCount) {
            return SimplifyResult.FAILED_TOO_BIG_RESULT
        }

        // (a + b) = 1 or (a + b) = 0 would give a value to the (a + b) group and simplify things.
        if (fields.size == 1 && minResult == maxResult) {
            knownValues.put(fields[0], minResult)
            listener.onValueSet(fields[0], minResult)
            return clearRule()
        }

        // (a + b) + (c + d) == 0 would give the value 0 to all field groups and simplify things
        if (maxResult == 0) {
            for (field in fields) {
                knownValues.put(field, 0)
                listener.onValueSet(field, 0)
            }
            return clearRule()
        }

        // (a + b) + (c + d) = 4 would give the value {Group.SIZE} to all Groups.
        if (totalCount == minResult) {
            for (field in fields) {
                val value = minResult * field.size / totalCount
                knownValues.put(field, value)
                listener.onValueSet(field, value)
            }
            return clearRule()
        }
        if (minResult <= 0 && maxResult >= totalCount) {
            // Rule is effectively useless
            clearRule()
        }
        return SimplifyResult.NO_EFFECT
    }

    private fun clearRule(): SimplifyResult {
        val simplifyResult = if (fields.isEmpty()) SimplifyResult.NO_EFFECT else SimplifyResult.SIMPLIFIED
        fields.clear()
        minResult = 0
        maxResult = 0
        return simplifyResult
    }

    override fun toString(): String {
        val rule = StringBuilder()
        rule.append(minResult)
        rule.append(" <= ")
        var fieldAdded = false
        for (field in fields) {
            if (fieldAdded) {
                rule.append(" + ")
            }
            fieldAdded = true
            rule.append(field.toString())
        }
        rule.append(" <= ")
        rule.append(maxResult)
        return rule.toString()
    }

    override fun copy(): BoundedFieldRule<T> {
        return BoundedFieldRule(this)
    }

    override fun fieldGroups(): MutableList<FieldGroup<T>> {
        return fields
    }

    override val smallestFieldGroup: FieldGroup<T>?
        get() {
            if (fields.isEmpty()) {
                return null
            }
            var result: FieldGroup<T> = fields[0]
            for (group in fields) {
                val size = group.size
                if (size == 1) {
                    return group
                }
                if (size < result.size) {
                    result = group
                }
            }
            return result
        }
}