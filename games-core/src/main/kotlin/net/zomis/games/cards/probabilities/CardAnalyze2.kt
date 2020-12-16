package net.zomis.games.cards.probabilities

import net.zomis.games.cards.CardZone
import kotlin.math.floor
import kotlin.math.min

typealias CardPredicate<T> = (T) -> Boolean
class ZoneRuleDef<T>(val name: Int, val zone: CardZone<T>, val size: Int, val predicate: CardPredicate<T>) {
    override fun toString(): String {
        return "Rule#$name($zone:$size)"
    }
}

data class CardGroup2<T>(val cards: Set<T>, val placementsLeft: Int)
data class ZoneRule2<T>(val zone: CardZone<T>, val size: Int, val groups: Set<CardGroup2<T>>) {
    fun order(): Int {
        return size * groups.size // Very simple ordering, actual order should be using int partitioning perhaps
    }

    fun isEmpty(): Boolean {
        return this.groups.isEmpty() && this.size == 0
    }
}

data class ZoneGroupAssignment<T>(val zone: CardZone<T>, val group: CardGroup2<T>, val count: Int) {
    override fun toString(): String {
        return "$zone $count of $group"
    }
}

class CardAnalyzeSolution<T>(val assignments: List<ZoneGroupAssignment<T>>) {
    fun getCombinationsOf(zone: CardZone<T>, predicate: (T) -> Boolean): DoubleArray {
        // Compare with how the old approach is working

        val zoneAssigns = assignments.filter { it.zone == zone && it.count > 0 }
        if (zoneAssigns.isEmpty()) throw IllegalStateException("Nothing assigned to zone $zone")
        val groups: List<CardGroup2<T>> = zoneAssigns.map { it.group }.distinct()
        var count = 0
        val groupsCount = groups.size
        val values = IntArray(groupsCount)
        val loopMaxs = IntArray(groupsCount)
        val predicateCounts = IntArray(groupsCount)
        for (i in groups.indices) {
            val group = groups[i]
            val value = zoneAssigns.single { it.group == group }.count
            val cardsMatchingPredicate = group.cards.count(predicate)
            values[i] = value
            loopMaxs[i] = min(value, cardsMatchingPredicate)
            predicateCounts[i] = cardsMatchingPredicate
            count += cardsMatchingPredicate
        }
        val result = DoubleArray(min(count, zone.size) + 1)
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
                combinations *= Combinatorics.NNKKwithDiv(group.cards.size, predicateCounts[i], values[i], loop[i])
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

//        ProgressAnalyze(zones, groups, rules, assignments)
        return result
    }

    fun getSpecificCombination(solution: Double): Map<CardZone<T>, List<T>> {
        // 8 zone assignments, each with a+a+a+a+a+a+a+a = 1
        var remainingCombinations = solution
        val result = mutableMapOf<CardZone<T>, List<T>>()

        val groups = this.assignments.map { it.group }.distinct()
        if (groups.size != 1) {
            throw UnsupportedOperationException("getting specific combination only supports one unique group at the moment")
        }
        val remainingCards = groups.single().cards.toMutableList()
        for (assignment in assignments) {

            val thisAssignmentTotalCombinations = Combinatorics.nCr(assignment.group.cards.size, assignment.count)
            val thisAssignmentCombination = remainingCombinations % thisAssignmentTotalCombinations
            remainingCombinations = floor(remainingCombinations / thisAssignmentTotalCombinations)

            val originalCards = assignment.group.cards.toList()
            val cards = Combinatorics.specificCombination(assignment.group.cards.size, assignment.count, thisAssignmentCombination + 1).map {
                originalCards[it]
            }
//            groups.getValue(assignment.group).removeAll(cards)
            result[assignment.zone] = cards
        }
        return result
        // a+a+a+a=2, b+b+b=2  ---> 6 * 3 --> divide by 6 for the first, then mod by 6
    }

    val combinations = assignments.groupBy { it.group.cards }.entries.fold(1.0) { acc, nextGroup ->
        val groupSize = nextGroup.key.size
        var positioned = 0
        var groupCombinations = 1.0
        for (assignment in nextGroup.value) {
            if (assignment.count == 0) {
                continue
            }
            val n = groupSize - positioned
            val r = assignment.count
            groupCombinations *= Combinatorics.nCr(n, r)
            positioned += r
        }
        acc * groupCombinations
    }
}
class CardAnalyzeSolutions<T>(val solutions: List<CardAnalyzeSolution<T>>) {
    val totalCombinations = solutions.sumByDouble { it.combinations }

    fun getProbabilityDistributionOf(zone: CardZone<T>, predicate: (T) -> Boolean): DoubleArray {
        val dbl = DoubleArray(zone.size + 1)

        for (sol in solutions) {
            val result = sol.getCombinationsOf(zone, predicate)
            result.indices.forEach {
                dbl[it] += result[it]
            }
        }

        for (i in dbl.indices) {
            dbl[i] = dbl[i] / totalCombinations
        }
        return dbl
    }

    fun getSpecificCombination(solution: Double): Map<CardZone<T>, List<T>> {
        require(solution >= 0 && solution < totalCombinations) { "solution must be an integer between 0 and total ($totalCombinations)" }
        check(solutions.isNotEmpty()) { "There are no solutions." }

        val iterator = solutions.iterator()
        var theSolution = iterator.next()
        var solutionsRemaining = solution
        while (solutionsRemaining > theSolution.combinations) {
            solutionsRemaining -= theSolution.combinations
            theSolution = iterator.next()
        }
        return theSolution.getSpecificCombination(solutionsRemaining)
    }

}

class CardAssignments<T>(val groups: Collection<CardGroup2<T>>, val assignments: List<ZoneGroupAssignment<T>>) {
    fun assign(vararg newAssignments: ZoneGroupAssignment<T>): CardAssignments<T> {
        val newAssignmentsPerGroup = newAssignments.groupBy {it.group}
                .mapValues { it.value.sumBy { assignment -> assignment.count } }
        val newGroups = groups.map {
            CardGroup2(it.cards, it.placementsLeft - (newAssignmentsPerGroup[it]
                    ?: 0))
        }
        return CardAssignments(newGroups, assignments + newAssignments.toList())
    }

    constructor(groups: Collection<CardGroup2<T>>): this(groups, emptyList())

//    remainingZones is taken care of by the regular ZoneRules
}

class CardsAnalyze2<T> {

    val zones = mutableListOf<CardZone<T>>()
    private val cards = mutableListOf<T>()
    private val rules = mutableListOf<ZoneRuleDef<T>>()

    fun addZone(zone: CardZone<T>) {
        zones.add(zone)
    }

    fun addCards(list: List<T>) {
        cards.addAll(list)
    }

    fun addRule(zone: CardZone<T>, size: Int, predicate: CardPredicate<T>) {
        rules.add(ZoneRuleDef(rules.size, zone, size, predicate))
    }

    fun addRule(zone: CardZone<T>, countStyle: CountStyle, size: Int, predicate: CardPredicate<T>) {
        TODO("countStyle not supported yet")
    }

    fun createCardGroups(): Map<Set<ZoneRuleDef<T>>, CardGroup2<T>> {
        return cards.groupBy { card -> rules.filter { it.predicate(card) }.toSet()
        }.mapValues { CardGroup2(it.value.toSet(), it.value.size) }
    }

    fun createRules(cardGroups: Map<Set<ZoneRuleDef<T>>, CardGroup2<T>>, groups: Set<CardGroup2<T>>): List<ZoneRule2<T>> {
        // Create rules for the known rules with card groups
        val ruleRules = rules.map { rule ->
            val cardGroupsInRule = cardGroups.entries.filter { entry -> entry.key.contains(rule) }.map { it.value }.toSet()
            ZoneRule2(rule.zone, rule.size, cardGroupsInRule)
        }

        // Create rules for zones to have their appropriate size
        val zoneRules = zones.map {
            ZoneRule2(it, it.size, groups)
        }
        return ruleRules.plus(zoneRules).sortedBy { it.order() }
    }

    fun rules(): List<ZoneRule2<T>> {
        val cardGroups = createCardGroups()
        val groups = cardGroups.values.toSet()
        return createRules(cardGroups, groups)
    }

    fun solve(): Sequence<CardAnalyzeSolution<T>> {
        /*
        * A B C D E
        * A B
        * A C
        */
        val cardGroups = createCardGroups()
        val groups = cardGroups.values.toSet()
        val rules = createRules(cardGroups, groups)
        val assignments = CardAssignments(groups)
        // Order rules by combinations possible, ascending

        return sequence {
            yieldAll(ProgressAnalyze(zones.toSet(), groups, rules, assignments).solve())
        }
    }

}

class ProgressAnalyze<T>(
        private val zones: Set<CardZone<T>>,
        private val groups: Set<CardGroup2<T>>,
        private val rules: List<ZoneRule2<T>>,
        private val assignments: CardAssignments<T>
) {

    fun findAutoAssignments(groups: Set<CardGroup2<T>>, rules: List<ZoneRule2<T>>): CardAssignments<T>? {
        var newAssignments = CardAssignments(groups)
        rules.forEach { rule ->
            // See also https://github.com/Zomis/Minesweeper-Analyze/blob/master/src/main/java/net/zomis/minesweeper/analyze/BoundedFieldRule.java#L64
            if (rule.size > rule.groups.sumBy { it.cards.size }) {
                return null
            }
            if (rule.size < 0) {
                return null
            }
            if (rule.groups.isEmpty() && rule.size != 0) {
                return null
            }
            if (rule.size == 0) {
                newAssignments = newAssignments.assign(*rule.groups.map { group -> ZoneGroupAssignment(rule.zone, group, 0) }.toTypedArray())
            }
            if (rule.groups.size == 1) {
                newAssignments = newAssignments.assign(ZoneGroupAssignment(rule.zone, rule.groups.single(), rule.size))
            }
        }
        return newAssignments
    }

    fun simplify(rules: List<ZoneRule2<T>>, assignments: CardAssignments<T>): List<ZoneRule2<T>> {
        if (assignments.assignments.isEmpty()) {
            return rules
        }
        val uniqueAssignments = assignments.assignments.distinct()
        val groupAssignments = uniqueAssignments.groupBy { it.group }
            .mapValues { entry -> entry.value.sumBy { it.count } }

        return rules.mapNotNull { rule ->
            val zoneAssignments = uniqueAssignments.filter { it.zone == rule.zone }
            val zoneAssignmentSum = zoneAssignments.filter { rule.groups.contains(it.group) }.sumBy { it.count }
            val zoneGroupsAssigned = zoneAssignments.map { it.group }

            val simplifiedRule = ZoneRule2(rule.zone, rule.size - zoneAssignmentSum,
                    rule.groups.minus(zoneGroupsAssigned).map {
                        val placementsDone = groupAssignments[it] ?: 0
                        CardGroup2(it.cards, it.placementsLeft - placementsDone)
                    }.toSet())
            if (simplifiedRule.isEmpty()) null else simplifiedRule
        }
    }

    fun solve(): Sequence<CardAnalyzeSolution<T>> {
        var fullAssignments: List<ZoneGroupAssignment<T>> = assignments.assignments.toList()
        var autoAssignments: CardAssignments<T> = assignments
        var simplifiedRules: List<ZoneRule2<T>> = rules

        var simplificationDone: Boolean
        do {
            autoAssignments = this.findAutoAssignments(autoAssignments.groups.toSet(), simplifiedRules) ?: return emptySequence()
            simplifiedRules = this.simplify(simplifiedRules, autoAssignments)
            fullAssignments = fullAssignments + autoAssignments.assignments
            simplificationDone = autoAssignments.assignments.isNotEmpty()
        } while (simplificationDone)

        /*
        println("# Zones")
        zones.forEach { println(it) }
        println("# Groups")
        groups.forEach { println(it) }
        println("# Rules")
        rules.forEach { println(it) }
        println("# Auto Assign")
        fullAssignments.forEach { println(it) }
        autoAssignments.groups.forEach { println(it) }
        println("# Simplified Rules")
        simplifiedRules.forEach { println(it) }
        */

        if (simplifiedRules.isEmpty()) {
            return sequenceOf(CardAnalyzeSolution(fullAssignments.distinct()))
        }
        if (simplifiedRules.size == 1) {
            val singleRule = simplifiedRules.single()
            val totalPlacementsLeft = singleRule.groups.sumBy { it.placementsLeft }
            if (singleRule.size != totalPlacementsLeft) {
                throw IllegalStateException("Sanity check failed: Rule wants ${singleRule.size} but $totalPlacementsLeft needs to be placed")
            }
            val remainingAssignments = singleRule.groups.map {
                ZoneGroupAssignment(singleRule.zone, it, it.placementsLeft)
            }

//            val combinedGroup = CardGroup2(singleRule.groups.flatMap { it.cards }.toSet(), singleRule.groups.sumBy { it.placementsLeft })
//            val remainingAssignment = ZoneGroupAssignment(singleRule.zone, combinedGroup, singleRule.size)
//            val solution = CardAnalyzeSolution(fullAssignments + remainingAssignment)
            val finalAssignments = fullAssignments + remainingAssignments
            val solution = CardAnalyzeSolution(finalAssignments.distinct())
            return sequenceOf(solution)
        }

        val rule = simplifiedRules.first()
        if (rule.size < 0) {
            // No solution
            return emptySequence()
        }
        val smallestGroup = rule.groups.minBy { it.placementsLeft }!!
        val maxAssignment = min(rule.size, smallestGroup.placementsLeft)

        return sequence {
            for (assignmentValue in 0..maxAssignment) {
                val assignments2 = findAutoAssignments(autoAssignments.groups.toSet(), simplifiedRules + ZoneRule2(rule.zone, assignmentValue, setOf(smallestGroup)))
                if (assignments2 == null) {
                    continue
                }
                val rules2 = simplify(simplifiedRules, assignments2)

                val assignmentList = fullAssignments + assignments2.assignments
                val next = ProgressAnalyze(zones, autoAssignments.groups.toSet(), rules2, CardAssignments(autoAssignments.groups, assignmentList))
                val solve = next.solve()
                yieldAll(solve)
            }
        }

        // Find smallest possibilities in zone
        // or find smallest number of zones that a card group can be in

//        val groupsCanBeIn = Map<Group, Set<Zone>>
//        val zonesCanHave = Map<Zone, Set<Group>>
// Find rule with lowest size, and then by lowest number of possible groups

    }

}