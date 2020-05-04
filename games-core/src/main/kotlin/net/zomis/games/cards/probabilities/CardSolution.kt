package net.zomis.games.cards.probabilities

import net.zomis.games.cards.CardZone
import kotlin.math.min

class CardSolution<Z : CardZone<*>, C>(results: List<ZoneRule<Z, C>>) {
    private val assignments = mutableMapOf<Z, CardAssignment<Z, C>>()
    val combinations: Double

    fun getAssignments(): Map<Z, CardAssignment<Z, C>> {
        return assignments.toMap()
    }

    fun validCheck(): Boolean {
        for ((key, value) in assignments) {
            val zoneSize: Int = key.size
            if (!value.isValidAssignment) return false
            val assigns = value.totalAssignments
            if (zoneSize != assigns) return false
        }
        return true
    }

    override fun toString(): String {
        return "Solution ${assignments.values} combinations = " + combinations + " placed $placed"
    }

    val placed = assignments.values.sumBy { it.totalAssignments }

    fun getProbabilityDistributionOf(zone: Z, predicate: (C) -> Boolean): DoubleArray {
        val zoneAssigns: CardAssignment<Z, C>? = assignments[zone]
//        println("Get probability distribution of: " + zoneAssigns + " solution combs " + combinations)
        val groups: List<CardGroup<C>> = zoneAssigns!!.groups.toMutableList()
        var count = 0
        val groupsCount = groups.size
        val values = IntArray(groupsCount)
        val loopMaxs = IntArray(groupsCount)
        val predicateCounts = IntArray(groupsCount)
        for (i in groups.indices) {
            val group = groups[i]
            val value = zoneAssigns.assigns[group]!!
            val cardsMatchingPredicate = countMatching(group.getCards(), predicate)
            values[i] = value
            loopMaxs[i] = min(value, cardsMatchingPredicate)
            predicateCounts[i] = cardsMatchingPredicate
            count += cardsMatchingPredicate
        }
        val result = DoubleArray(min(count, zone!!.size) + 1)
//        println("Creating array with length " + result.size)
        // Zone{Player1 Hand} --> Assign:Zone{Player1 Hand}={CG:[4 CLUBS + 9 others]=0, CG:[9 CLUBS + 30 others]=13}
// Zone{Player1 Hand} --> Assign:Zone{Player1 Hand}={CG:[2 CLUBS + 7 others]=2, CG:[2 CLUBS + 7 others]=4, CG:[9 CLUBS + 30 others]=7}
// Zone{Player0 __Y__} --> Assign:Zone{Player0 __Y__}={CG:[Card:a, Card:a]=0, CG:[Card:d, Card:d]=1, CG:[Card:b, Card:c]=0, CG:[Card:b, Card:c]=1}
//		List<Map<CardGroup<C>, Integer>> mapList = new ArrayList<>();
        val loop = IntArray(groups.size)
        val lastIndex = loop.size - 1
        while (loop[0] <= loopMaxs[0]) {
//            print(loop.contentToString() + " = ")
            var loopSum = 0
            var combinations = 1.0
            for (i in 0 until groupsCount) {
                loopSum += loop[i]
                val group = groups[i]
                combinations *= Combinatorics.NNKKwithDiv(group.size(), predicateCounts[i], values[i], loop[i])
//                println(combinations)
            }
            result[loopSum] += combinations
            // Increase the count
            loop[lastIndex]++
            var overflow = lastIndex
            while (overflow >= 1 && loop[overflow] > loopMaxs[overflow]) {
                loop[overflow] = 0
                loop[overflow - 1]++
                overflow--
            }
        }
        // a + a + a + b + b = 2, a + a + c + c = 1
        for (i in result.indices) {
            result[i] = result[i] * combinations
        }
//        println("Result = " + result.contentToString())
//        println("Sum = " + result.sum())
        return result
    }

    private fun countMatching(cards: Collection<C>, predicate: (C) -> Boolean): Int = cards.count(predicate)

    fun getAssignment(zone: Z, group: CardGroup<C>): Int {
        val assigns = assignments[zone]
                ?: throw IllegalArgumentException("Zone $zone does not have an assignment in this solution")
        return assigns.assigns[group]
                ?: throw IllegalArgumentException("Group $group does not have an assignment for zone $zone")
    }

    init {
        val positioned: MutableMap<CardGroup<C>, Int?> = HashMap()
        var combinations = 1.0
        //		System.out.println("Creating solution");
        for (rule in results) {
            assignments[rule.zone] = rule.getAssignments()
            //			System.out.println("Rule " + rule);
            for ((key, value) in rule.getAssignments().assigns) {
                if (value == 0) continue  // Booooooring!
                val positionedTemp = positioned[key]
                val positionedAlready = positionedTemp ?: 0
                val n = key.size() - positionedAlready
                //				System.out.println(ee + " nCr: " + n + ", " + r);
                combinations *= Combinatorics.nCr(n, value!!)
                positioned[key] = positionedAlready + value
                if (!positioned.containsKey(key)) { /* 8 cards (4+4), 2 zones (4+4) ---> ZomisUtils.NNKKwithDiv(8, 4, 4, x)
					 * 8 cards (5+3), 2 zones (4+4) ---> ZomisUtils.NNKKwithDiv(8, 5/3, 4, x)
					 * 9 cards (3+3+3), 3 zones (3+3+3) ---> ZomisUtils.NNKKwithDiv(9, 3, 3, x)
					 *
					 * */
                } else {
                }
                //				ZomisUtils.nCr(n, r);
            }
        }
        this.combinations = combinations
    }
}