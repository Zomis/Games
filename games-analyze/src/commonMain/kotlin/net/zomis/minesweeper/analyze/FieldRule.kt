package net.zomis.minesweeper.analyze

/**
 * A constraint of a number of fields or [FieldGroup]s that should have a specific sum
 *
 * @author Simon Forsberg
 * @param <T> Field type
</T> */
class FieldRule<T> : BoundedFieldRule<T> {
    /**
     * Create a copy of an existing rule.
     *
     * @param copyFrom Rule to copy
     */
    private constructor(copyFrom: FieldRule<T>) : super(
        copyFrom.cause,
        copyFrom.fields,
        copyFrom.result,
        copyFrom.result
    )

    /**
     * Create a rule from a list of fields and a result (create a new FieldGroup for it)
     *
     * @param cause The reason for why this rule is added (optional, may be null)
     * @param rule Fields that this rule applies to
     * @param result The value that should be forced for the fields
     */
    constructor(cause: T?, rule: Collection<T>, result: Int) : super(cause, rule, result, result)
    internal constructor(cause: T?, group: FieldGroup<T>, result: Int) : super(
        cause,
        mutableListOf(),
        result,
        result
    ) {
        fields.add(group!!)
    }

    val fieldGroupsField: Collection<FieldGroup<T>> get() = fields
    val fieldsCountInGroups: Int
        get() {
            var fieldsCounter = 0
            for (group in fields) {
                fieldsCounter += group.size
            }
            return fieldsCounter
        }
    val result: Int
        get() = minResult

    fun nCr(): Double {
        check(fields.size == 1) { "Rule has more than one group." }
        return Combinatorics.nCr(fieldsCountInGroups, minResult)
    }

    override fun toString(): String {
        val rule = StringBuilder()
        for (field in fields) {
            if (rule.length > 0) {
                rule.append(" + ")
            }
            rule.append(field.toString())
        }
        rule.append(" = ")
        rule.append(result)
        return rule.toString()
    }

    override fun copy(): FieldRule<T> {
        return FieldRule(this)
    }
}