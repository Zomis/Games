package net.zomis.games.cards.probabilities

import net.zomis.games.cards.CardZone
import kotlin.collections.MutableMap.MutableEntry

class CardsAnalyze<Z : CardZone<*>, C> : CardSolutionCallback<Z, C> {
    private val rules: MutableList<ZoneRule<Z, C>> = mutableListOf()
    private val cards: MutableList<C> = mutableListOf()
    private val zones: MutableList<Z> = mutableListOf()
    private val unplacedCards: MutableMap<CardGroup<C>, Int?> = HashMap()
    private val assignmentProgress: MutableList<ZoneRule<Z, C>> = mutableListOf()
    private val solutionCallback: CardSolutionCallback<Z, C>
    private val solutions: MutableList<CardSolution<Z, C>> = mutableListOf()

    // GroupValues<Field> knownValues, List<FieldRule<Field>> unsolvedRules
    constructor() {
        solutionCallback = this
    }

    constructor(callback: CardSolutionCallback<Z, C>) {
        solutionCallback = callback
    }

    fun addCards(cards: Iterable<C>): CardsAnalyze<Z, C> {
        check(rules.isEmpty()) { "Cannot add more cards when rules has been added" }
        for (c in cards) {
            this.cards.add(c)
        }
        return this
    }

    fun addZone(zone: Z): CardsAnalyze<Z, C> {
        zones.add(zone)
        return this
    }

    fun addRule(zone: Z, count: Int, predicate: (C) -> Boolean) {
        this.addRule(zone, CountStyle.EQUAL, count, predicate)
    }

    fun addRule(zone: Z, compare: CountStyle, count: Int, predicate: (C) -> Boolean) {
        if (!zones.contains(zone)) {
            throw IllegalArgumentException("Zone " + zone +
                    " has not been added to this analyze. "
                    + "Call `addZone` if this zone should be considered. "
                    + "Don't add this rule if it should not be considered.")
        }
        rules.add(ZoneRule(zone, compare, count, findCards(predicate)))
    }

    fun solve(): CardSolutions<Z, C> {
        solveOnce()
        solveInternal()
        return CardSolutions<Z, C>(solutions)
    }

    private fun solveOnce() {
        verifySums()
        addRulesAboutMissingCards()
        // Create/Split CardGroups
        splitGroups()
        calculateUnplacedCards()
    }

    private fun solveInternal() { //		System.out.println("Before simplification:");
//		this.outputRules();
// Simplify rules
        simplify()
        //		System.out.println("After simplification:");
//		this.outputRules();
// Iterate and solve
        solveByIteration()
    }

    private fun solveByIteration() {
        val focusZone = determineFocusZone()
        if (focusZone == null) {
            solutionCallback.onSolved(assignmentProgress)
            return
        }
        val focusGroup = getFocusGroupFor(focusZone)
        val unplaced = unplacedCards[focusGroup]!!
        for (i in 0..unplaced) { //			System.out.println("Solving by iteration: " + focusGroup + " = " + i + " in " + focusZone);
            val copy = internalCopy(true)
            copy.assign(focusZone.zone, focusGroup, i)
            copy.solveInternal()
            //			System.out.println("-------------");
        }
    }

    private fun determineFocusZone(): ZoneRule<Z, C>? {
        //		ZoneRule<Z, C> zoneRule = this.assignmentProgress.get(2);
        //		if (zoneRule.getCompare() != CountStyle.DONE)
        //			return zoneRule;
        for (rule in assignmentProgress) {
            if (rule.getCompare() !== CountStyle.DONE) {
                return rule
            }
        }
        return null
    }

    private fun getFocusGroupFor(focusZone: ZoneRule<Z, C>): CardGroup<C> {
        for ((key, value) in focusZone.getAssignments().assigns) {
            if (value != null) continue
            return key
        }
        throw IllegalArgumentException("Rule does not have any unset groups: $focusZone")
    }

    fun createCopy(): CardsAnalyze<Z, C> {
        val copy = internalCopy(false)
        copy.zones.addAll(zones)
        copy.cards.addAll(cards)
        return copy
    }

    private fun internalCopy(useSameSolutionCallback: Boolean): CardsAnalyze<Z, C> {
        val result: CardsAnalyze<Z, C>
        if (useSameSolutionCallback) result = CardsAnalyze(solutionCallback) else result = CardsAnalyze()
        for (assignmentProg in assignmentProgress) {
            result.assignmentProgress.add(assignmentProg!!.copy())
        }
        for (rule in rules) {
            result.rules.add(rule!!.copy())
        }
//		result.assignmentProgress
//		result.rules
        result.unplacedCards.putAll(unplacedCards)
        return result
    }

    private fun calculateUnplacedCards() {
        val first = assignmentProgress[0]
        for (group in first!!.getAssignments()!!.groups) {
            unplacedCards[group] = group.size()
        }
    }

    /**
     * Loop through the rules and check for `EQUAL(x) = Assign:{only one group}`
     */
    private fun simplify() {
        var simplificationDone: Boolean
        do {
            simplificationDone = false
            for (rule in rules) {
                if (rule!!.isEqualKnown) {
                    val assignment: MutableEntry<CardGroup<C>, Int?> = rule.getAssignments().assigns.entries.iterator().next()
                    // TODO: Law of Demeter. rule.getOnlyAssignment(); ?
                    assignment.setValue(rule.count)
                    val group = assignment.key
                    assign(rule.zone, group, rule.count) // Note that the assignment progress rule for `rule.getZone` does not match `rule` here!
                    rule.clear()
                    simplificationDone = true
                }
            }
            // TODO: If assignments is complete, then try to scan for groups that can only be within one zone
            for (progress in assignmentProgress) {
                if (progress!!.synchronizeWith(unplacedCards)) simplificationDone = true
            }
        } while (simplificationDone)
    }

    private fun assign(zone: Z?, group: CardGroup<C>, count: Int) {
        val progress = getAssignmentProgressFor(zone)
        progress!!.assign(group, count, unplacedCards)
    }

    private fun getAssignmentProgressFor(zone: Z?): ZoneRule<Z, C>? { // TODO: Probably make assignment progress a Map<Z, ZoneRule<Z, C>> instead, this is just nuts right now.
        for (rule in assignmentProgress) {
            if (rule!!.zone === zone) {
                return rule
            }
        }
        throw IllegalStateException("No assignment progress found for zone $zone")
    }

    private fun verifySums() {
        if (zones.isEmpty()) throw IllegalStateException("No zones have been added. Please call `addZone`")
        if (cards.isEmpty()) throw IllegalStateException("No cards have been added. Please call `addCards`")
        val totalZoneCards = zones.sumBy { it.size }
        if (totalZoneCards != cards.size) throw IllegalStateException("Mismatch cards: " + cards.size + " cards and room for $totalZoneCards")
    }

    private fun addRulesAboutMissingCards() { /* 2x + 2y + 2z = 2a + b + c + 2d
		 * 2y = 0a
		 * 2x = 0d
		 * --->
		 * 2x = ?a + ?bc
		 * 2y = ?bc + ?d
		 * 2z = ?a + ?bc + ?d
		 **/
        for (zone in zones) {
            assignmentProgress.add(ZoneRule.unknown(zone, cards))
        }
    }

    private fun splitGroups() {
        val rules: MutableList<ZoneRule<Z, C>> = rules.toMutableList()
        rules.addAll(assignmentProgress)
        var splitPerformed = true
        while (splitPerformed) {
            splitPerformed = false
            for (a in rules) {
                for (b in rules) {
                    val result = a.checkIntersection(b)
                    if (result) {
                        splitPerformed = true
                    }
                }
            }
        }
    }

    private fun findCards(predicate: (C) -> Boolean): List<C> {
        val matchingCards: MutableList<C> = mutableListOf()
        for (card in cards) {
            if (predicate(card)) matchingCards.add(card)
        }
        return matchingCards
    }

    fun outputRules() {
        val log: (Any) -> Unit = { println(it) }
        val hr: () -> Unit = { println() }
        hr()
        log("Rules:")
        rules.forEach(log)
        hr()
        log("Assignment Progress:")
        assignmentProgress.forEach(log)
        hr()
        log("Unplaced cards:")
        log(unplacedCards)
        hr()
    }

    override fun onSolved(results: List<ZoneRule<Z, C>>) {
        val sol = CardSolution(results)
        if (sol.validCheck()) {
            solutions.add(sol)
        } else {
//			System.out.println(this + " INVALID Solution has been found!!!".toUpperCase() + " -- " + sol);
//			results.forEach(System.out::println);
//			System.out.println();
//			System.out.println();
        }
    }
}