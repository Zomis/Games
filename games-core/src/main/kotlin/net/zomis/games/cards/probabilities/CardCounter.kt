package net.zomis.games.cards.probabilities

import net.zomis.games.cards.CardZone

class CardCounter<T> {

    val analyze = CardsAnalyze<CardZone<T>, T>()

    fun hiddenZones(vararg zones: CardZone<T>): CardCounter<T> {
        zones.forEach {
            analyze.addZone(it)
            analyze.addCards(it.toList())
        }
        return this
    }

    fun knownZones(vararg zones: CardZone<T>): CardCounter<T> {
        hiddenZones(*zones)
        zones.forEach {
            analyze.addRule(it, it.size) { c -> it.cards.contains(c) }
        }
        return this
    }

    fun solve(): CardSolutions<CardZone<T>, T> {
        val analyzeCopy = analyze.createCopy()
        return analyzeCopy.solve()
    }

    fun exactRule(zone: CardZone<T>, value: Int, predicate: (T) -> Boolean): CardCounter<T> {
        analyze.addRule(zone, value, predicate)
        return this
    }

    fun rule(zone: CardZone<T>, compare: CountStyle, value: Int, predicate: (T) -> Boolean): CardCounter<T> {
        analyze.addRule(zone, compare, value, predicate)
        return this
    }

}