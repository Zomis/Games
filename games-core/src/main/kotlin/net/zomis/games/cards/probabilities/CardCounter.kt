package net.zomis.games.cards.probabilities

import net.zomis.games.cards.CardZone
import net.zomis.games.cards.probabilities.v2.CardAnalyzeSolution
import net.zomis.games.cards.probabilities.v2.CardAssignments
import net.zomis.games.cards.probabilities.v2.CardsAnalyze2

class CardCounter<T> {

    val analyze = CardsAnalyze<CardZone<T>, T>()
    val analyze2 = CardsAnalyze2<T>()

    fun hiddenZones(vararg zones: CardZone<T>): CardCounter<T> {
        zones.forEach {
            analyze.addZone(it)
            analyze.addCards(it.toList())
            analyze2.addZone(it)
            analyze2.addCards(it.toList())
        }
        return this
    }

    fun knownZones(vararg zones: CardZone<T>): CardCounter<T> {
        hiddenZones(*zones)
        zones.forEach {
            analyze.addRule(it, it.size) { c -> it.cards.contains(c) }
            analyze2.addRule(it, it.size) { c -> it.cards.contains(c) }
        }
        return this
    }

    fun solve(): CardSolutions<CardZone<T>, T> {
        val analyzeCopy = analyze.createCopy()
        return analyzeCopy.solve()
    }

    private fun sanityCheck(solution: CardAnalyzeSolution<T>) {
        val assigned = solution.assignments.sumBy { it.count }
        val group = solution.assignments.map { it.group }.distinct().sumBy { it.cards.size }
        val zones = solution.assignments.map { it.zone }.distinct().sumBy { it.size }
        val realZones = this.analyze2.zones.sumBy { it.size }
        println("SANITY CHECK assigned $assigned groups $group zones $zones realZones $realZones")
    }

    fun solve2(): Sequence<CardAnalyzeSolution<T>> {
        return analyze2.solve()
    }

    fun exactRule(zone: CardZone<T>, value: Int, predicate: (T) -> Boolean): CardCounter<T> {
        analyze.addRule(zone, value, predicate)
        analyze2.addRule(zone, value, predicate)
        return this
    }

    fun rule(zone: CardZone<T>, compare: CountStyle, value: Int, predicate: (T) -> Boolean): CardCounter<T> {
        analyze.addRule(zone, compare, value, predicate)
        analyze2.addRule(zone, compare, value, predicate)
        return this
    }

}