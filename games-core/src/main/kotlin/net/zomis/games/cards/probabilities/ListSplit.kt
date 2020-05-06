package net.zomis.games.cards.probabilities

class ListSplit<T> private constructor(val onlyA: List<T>, val both: List<T>, val onlyB: List<T>) {

    fun splitPerformed(): Boolean {
        return onlyA.isNotEmpty() || onlyB.isNotEmpty()
    }

    companion object {
        /**
         * Performs a split if necessary, or returns null if no split was done
         *
         * @param a One of the lists to split
         * @param b The other list to split
         * @return A [ListSplit] object, or null if the lists refer to the same object or if they have no groups in common
         */
        fun <T> split(a: MutableList<T>, b: List<T>): ListSplit<T>? {
            if (a === b) return null
            if (a.intersect(b).isEmpty()) return null
            var both: MutableList<T> = a.toMutableList()
            val onlyA: MutableList<T> = a.toMutableList()
            val onlyB: MutableList<T> = b.toMutableList()
            both.retainAll(b)
            onlyA.removeAll(both)
            onlyB.removeAll(both)
            // Check if ALL fields are in common
            if (onlyA.isEmpty() && onlyB.isEmpty()) { // If this is called in a loop an inf-loop can occur if we don't do this because we're creating a NEW object all the time to hold them both.
// We should reuse one of the existing ones and go back to using == above.
                both = a
            }
            return ListSplit(onlyA, both, onlyB)
        }
    }

}