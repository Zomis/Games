package net.zomis.games.cards.probabilities

import net.zomis.games.cards.CardZone
import kotlin.math.min

class ZoneRule<Z : CardZone<*>, C> {
    val zone: Z
    private var compare: CountStyle?
    val count: Int
    private val assignments: CardAssignment<Z, C>

    private constructor(zone: Z, cards: MutableCollection<C>) {
        this.zone = zone
        compare = null
        count = 0
        val assigns: MutableMap<CardGroup<C>, Int?> = HashMap()
        assigns[CardGroup(cards)] = null
        assignments = CardAssignment(zone, assigns)
    }

    private constructor(previous: ZoneRule<Z, C>) {
        zone = previous.zone
        compare = previous.compare
        count = previous.count
        assignments = CardAssignment(zone, HashMap(previous.assignments.assigns))
    }

    constructor(zone: Z, compare: CountStyle, count: Int, cards: List<C>) {
        this.zone = zone
        this.compare = compare
        this.count = count
        val assigns: MutableMap<CardGroup<C>, Int?> = HashMap()
        val grp: CardGroup<C> = CardGroup(cards)
        if (compare === CountStyle.EQUAL) {
            assigns[grp] = count
        } else assigns[grp] = null
        assignments = CardAssignment(zone, assigns)
    }

    fun getAssignments(): CardAssignment<Z, C> {
        return assignments
    }

    fun checkIntersection(other: ZoneRule<Z, C>): Boolean {
        if (other === this) return false
        val fieldsCopy: MutableList<CardGroup<C>> = assignments.groups.toMutableList()
        val ruleFieldsCopy: MutableList<CardGroup<C>> = other.assignments.groups.toMutableList()
        for (groupA in fieldsCopy) {
            for (groupB in ruleFieldsCopy) {
                if (groupA === groupB) continue
                val splitResult: ListSplit<C> = groupA.splitCheck(groupB) ?: continue
                // nothing to split
                if (!splitResult.splitPerformed()) continue
                //				System.out.println(splitResult);
                val both: CardGroup<C> = CardGroup(splitResult.both)
                val onlyA: CardGroup<C> = CardGroup(splitResult.onlyA)
                val onlyB: CardGroup<C> = CardGroup(splitResult.onlyB)
                assignments.assigns.remove(groupA)
                if (!onlyA.isEmpty) assignments.assigns.put(onlyA, null)
                if (!both.isEmpty) assignments.assigns.put(both, null)
                other.assignments.assigns.remove(groupB)
                if (!both.isEmpty) other.assignments.assigns.put(both, null)
                if (!onlyB.isEmpty) other.assignments.assigns.put(onlyB, null)
                return true
            }
        }
        return false
    }

    override fun toString(): String {
        return "Rule:" + zone + " = " + compare + "(" + count + ") " + assignments
    }

    val isEqualKnown: Boolean
        get() = compare === CountStyle.EQUAL && assignments.groups.size == 1

    private fun checkFinished(): Boolean {
        for (ee in assignments.assigns.values) {
            if (ee == null) return false
        }
        compare = CountStyle.DONE
        return true
    }

    fun clear() {
        compare = CountStyle.DONE
        assignments.assigns.clear()
    }

    fun copy(): ZoneRule<Z, C> {
        return ZoneRule(this)
    }

    private val onlyUnassignedGroup: CardGroup<C>?
        private get() {
            var unassigned: CardGroup<C>? = null
            for (ee in assignments.assigns.entries) {
                if (ee.value == null) {
                    if (unassigned != null) return null
                    unassigned = ee!!.key
                }
            }
            return unassigned
        }

    fun getUnassignedGroupsSum(unassigned: MutableMap<CardGroup<C>, Int?>): Int {
        var count = 0
        for (ee in assignments.assigns.entries) {
            if (ee.value == null) {
                count += unassigned[ee.key]!!
            }
        }
        return count
    }

    fun synchronizeWith(unplacedCards: MutableMap<CardGroup<C>, Int?>): Boolean {
        for (ee in assignments.assigns.entries) {
            if (ee.value == null) {
                if (unplacedCards[ee.key] == 0) { //					this.assignments.assign(ee.getKey(), 0);
                    assign(ee.key, 0, unplacedCards)
                    return true
                }
            }
        }
        if (checkForOnlyOneUnassignedGroup(unplacedCards)) return true
        return if (checkForUnassignedGroupsSumMatchingRemainingSpace(unplacedCards)) true else false
    }

    fun assign(group: CardGroup<C>, count: Int, unplacedCards: MutableMap<CardGroup<C>, Int?>) {
        assignments.assign(group, count)
        unplacedCards[group] = unplacedCards[group]!! - count
        // Check if `progress` is complete (i.e. sum of assignments == size)
        checkFinished()
    }

    fun checkForOnlyOneUnassignedGroup(unassignedCards: MutableMap<CardGroup<C>, Int?>): Boolean {
        // Rule:Zone{Y} = null(0) Assign:Zone{Y}={CG:[Card:a, Card:a]=0, CG:[Card:b, Card:c, Card:b, Card:c]=null, CG:[Card:d, Card:d]=0}
        // CG:[Card:b, Card:c, Card:b, Card:c]=null is the only unassigned group
        // {CG:[Card:a, Card:a]=2, CG:[Card:b, Card:c, Card:b, Card:c]=2, CG:[Card:d, Card:d]=2}
        val unassigned: CardGroup<C>? = onlyUnassignedGroup
        if (unassigned != null) { // Find the unassigned group and assign it to whatever space is available.
            val count: Int = min(unassignedCards[unassigned]!!, remainingSpace)
            assign(unassigned, count, unassignedCards)
        }
        return unassigned != null
    }

    fun getCompare(): CountStyle? {
        return compare
    }

    private val remainingSpace: Int
        get() {
            var count: Int = zone.size
            for (ee in assignments.assigns.values) {
                if (ee != null) count -= ee
            }
            return count
        }

    fun checkForUnassignedGroupsSumMatchingRemainingSpace(unassignedCards: MutableMap<CardGroup<C>, Int?>): Boolean {
        // Rule:Zone{Y} = null(0) Assign:Zone{Y}={CG:[Card:a, Card:a]=0, CG:[Card:b, Card:b]=null, CG:[Card:c, Card:c]=null, CG:[Card:d, Card:d]=0}
        // Unassigned: {CG:[Card:a, Card:a]=2, CG:[Card:b, Card:b]=1, CG:[Card:c, Card:c]=1, CG:[Card:d, Card:d]=2}
        return if (getUnassignedGroupsSum(unassignedCards) == remainingSpace) { // Find the unassigned groups and assign them to the
            false
        } else false
    }

    companion object {
        fun <Z : CardZone<*>, C> unknown(zone: Z, cards: MutableCollection<C>): ZoneRule<Z, C> {
            return ZoneRule(zone, cards)
        }
    }
}