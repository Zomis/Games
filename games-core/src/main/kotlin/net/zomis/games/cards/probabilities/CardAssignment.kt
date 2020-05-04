package net.zomis.games.cards.probabilities

class CardAssignment<Z, C>(val zone: Z, val assigns: MutableMap<CardGroup<C>, Int?>) {
    //internal constructor() : this(null, HashMap())

    fun assign(group: CardGroup<C>, value: Int) {
        check(assigns.containsKey(group)) { "Assign does not contain key: $group. Unable to assign it value $value" }
        check(assigns[group] == null) { "Group has already been assigned: " + group + " = " + assigns[group] }
        assigns[group] = value
    }

    val groups: Set<CardGroup<C>>
        get() = assigns.keys

    override fun toString(): String {
        return "$zone=$assigns"
    }

    val totalAssignments: Int
        get() {
            var i = 0
            for (value in assigns.values) {
                if (value != null) i += value
            }
            return i
        }

    // TODO: Throw exception when assigning a value less than zero. Fail fast.
    val isValidAssignment: Boolean
        get() {
            for ((key, assigns) in assigns) {
                val size = key.size()
                if (assigns == null) { continue }
                if (assigns > size) return false
                if (assigns < 0) // TODO: Throw exception when assigning a value less than zero. Fail fast.
                    return false
            }
            return true
        }

}