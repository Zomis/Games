package net.zomis.games.components

import net.zomis.games.cards.Card
import net.zomis.games.cards.CardZone
import net.zomis.games.cards.CardZoneI
import net.zomis.games.cards.DeckDirection
import net.zomis.games.dsl.ReplayStateI

/**
* A class that keeps track of cards. Like CardZone but may remember which card is the 5th from top etc.
*/
class SemiKnownCardZone<T: Any>(cards: List<T> = emptyList(), private val matcher: (T) -> String): CardZoneI<T> {
    override var name: String? = null

    override val size: Int get() = cards.size
    override val indices: IntRange get() = cards.indices

    private val unassignedCards: MutableList<T> = mutableListOf()// Remove from here when picking random
    private val cards: MutableList<T?> = cards.toMutableList()

    fun knownCards(): List<IndexedValue<T>> {
        return cards.toList().withIndex().filter { it.value != null }.map { IndexedValue(it.index, it.value!!) }
    }

    fun top(replayable: ReplayStateI, stateKey: String, count: Int): Sequence<Card<T>> {
        return learnCards(0 until count, replayable, stateKey)
    }

    private fun learnCards(range: IntRange, replayable: ReplayStateI, stateKey: String): Sequence<Card<T>> {
        require(range.first >= 0 && range.last < size) { "Requested cards $range is outside of zone size $indices" }
        val unknownCount = cards.slice(range).count { it == null }
        val newLearntCards = this.randomFromUnknown(replayable, unknownCount, stateKey).toList()
        var newLearntCount = 0

        return range.asSequence().map { i ->
            val value = cards[i]
            if (value == null) {
                val learnedCard = newLearntCards[newLearntCount++]
                unassignedCards.remove(learnedCard)
                cards[i] = learnedCard
                learnedCard
            } else {
                value
            }
        }.map { card(it) }
    }

    override fun remove(card: Card<T>): T {
        val value = this.cards.removeAt(card.index)
        check(value == card.card) { "Card $card has moved away from index ${card.index} in zone $this" }
        return value
    }

    override fun add(card: T) {
        cards.add(card)
    }

    fun deal(replayable: ReplayStateI, stateKey: String, count: Int, destinations: List<CardZoneI<T>>, transformer: (T) -> T = { it }) {
        val cardsToDeal = learnCards(0 until count, replayable, stateKey).toList()
        cardsToDeal.indices.forEach { index ->
            val cardValue = cards[0]!!
            Card(this, 0, cardValue).transformTo(destinations[index % destinations.size], transformer)
        }
    }

    fun insertAt(direction: DeckDirection, stepsAway: Int, cards: Collection<T>) {
        require(stepsAway in 0 until size)
        when (direction) {
            DeckDirection.TOP -> this.cards.addAll(stepsAway, cards)
            DeckDirection.BOTTOM -> this.cards.addAll(size - stepsAway, cards)
        }
    }

    fun shuffle(): SemiKnownCardZone<T> {
        this.unassignedCards.addAll(this.cards.filterNotNull())
        this.unassignedCards.shuffle()
        this.cards.fill(null)
        return this
    }

    override fun card(value: T): Card<T> {
        val index = cards.indexOf(value)
        check(index >= 0) { "$value is not a known card in zone $this (indexOf value was $index among $cards)" }
        return Card(this, index, value)
    }

    private fun randomFromUnknown(replayable: ReplayStateI, count: Int, stateKey: String): List<T> {
        require(count <= this.unassignedCards.size) { "Requesting more cards $count than are unknown in zone $this" }
        return replayable.randomFromList(stateKey, this.unassignedCards, count) { matcher.invoke(it) }
    }

}