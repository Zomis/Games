package net.zomis.minesweeper.analyze

class FieldGroupSplit<T> private constructor(
    val onlyA: FieldGroup<T>,
    val both: FieldGroup<T>,
    val onlyB: FieldGroup<T>
) {

    fun splitPerformed(): Boolean {
        return onlyA.isNotEmpty() || onlyB.isNotEmpty()
    }

    override fun toString(): String {
        return "FieldGroupSplit:$onlyA -- $both -- $onlyB"
    }

    companion object {
        /**
         * Split (and combine) all FieldGroups at once, by grouping them by which rules that contain each field.
         */
        fun <T> superSplit(rules: List<RuleConstraint<T>>) {
            val allStartGroups = rules.flatMap { it.fieldGroups() }.distinct()
            val allFields = allStartGroups.flatMap { it.fields }.distinct()
            val fieldCountBefore = allFields.size
            val intermediate: Map<List<RuleConstraint<T>>, List<T>> = allFields
                .groupBy { f -> rules.filter { it.fieldGroups().any { fg -> fg.contains(f) } } }

            rules.forEach { it.fieldGroups().clear() }

            intermediate.forEach {
                val fieldGroup = FieldGroup(it.value)
                it.key.forEach { rule -> rule.fieldGroups().add(fieldGroup) }
            }
            val fieldCountAfter = intermediate.values.sumOf { it.size }
            check(fieldCountBefore == fieldCountAfter) { "fieldCountBefore ($fieldCountBefore) != fieldCountAfter ($fieldCountAfter): $intermediate" }
        }

        fun <T> split(a: FieldGroup<T>, b: FieldGroup<T>): FieldGroupSplit<T>? {
            if (a === b) {
                return null
            }
            if (disjoint(a, b)) {
                return null // Return if the groups have no fields in common
            }
            var both = FieldGroup(a)
            val onlyA = FieldGroup(a)
            val onlyB = FieldGroup(b)
            both.retainAll(b)
            if (both.isEmpty) return null
            onlyA.removeAll(both)
            onlyB.removeAll(both)

            // Check if ALL fields are in common
            if (onlyA.isEmpty && onlyB.isEmpty) {
                // If this is called in a loop an inf-loop can occur if we don't do this because we're creating a NEW object all the time to hold them both.
                // We should reuse one of the existing ones and go back to using == above.
                both = a
            }
            return FieldGroupSplit(onlyA, both, onlyB)
        }

        private fun <T> disjoint(a: FieldGroup<T>, b: FieldGroup<T>): Boolean {
            if (a.isEmpty || b.isEmpty) return true

            val iterate = if (a.size < b.size) a else b
            val contains = if (iterate === a) b else a
            return iterate.all { !contains.contains(it) }
        }

        fun <T> splitHash(a: FieldGroup<T>, b: FieldGroup<T>): FieldGroupSplit<T>? {
            if (a === b || a.isEmpty || b.isEmpty) {
                return null
            }
            val aIsSmall = a.size <= b.size
            val smallestGroup = if (aIsSmall) a else b
            val setBig: Set<T> = (if (aIsSmall) b else a).toMutableSet()
            val setBoth: MutableSet<T> = HashSet(smallestGroup.size)
            val aOnly = FieldGroup(ArrayList<T>(a.size))
            val bOnly = FieldGroup(ArrayList<T>(b.size))
            val smallOnly = if (aIsSmall) aOnly else bOnly
            val bigOnly = if (aIsSmall) bOnly else aOnly
            for (`val` in smallestGroup) {
                if (setBig.contains(`val`)) {
                    setBoth.add(`val`)
                } else {
                    smallOnly.add(`val`)
                }
            }
            if (setBoth.isEmpty()) {
                return null
            }
            for (`val` in setBig) {
                if (!setBoth.contains(`val`)) {
                    bigOnly.add(`val`)
                }
            }
            return FieldGroupSplit(aOnly, if (aOnly.isEmpty && bOnly.isEmpty) a else FieldGroup(setBoth.toList()), bOnly)
        }
    }
}