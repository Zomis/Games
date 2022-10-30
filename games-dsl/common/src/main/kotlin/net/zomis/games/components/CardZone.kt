package net.zomis.games.cards

import net.zomis.games.dsl.Replayable
import net.zomis.games.dsl.ReplayableScope

enum class DeckDirection {
    TOP, BOTTOM
}

interface CardZoneI<T> {
    var name: String?
    val size: Int
    val indices: IntRange
    fun card(value: T): Card<T>
    fun isEmpty(): Boolean = size == 0
    fun isNotEmpty(): Boolean = !isEmpty()
    fun add(card: T)
    fun remove(card: Card<T>): T
}

data class Card<T>(val zone: CardZoneI<T>, val index: Int, val card: T) {
    fun remove(): T {
        val value = this.zone.remove(this)
        if (value != this.card) throw IllegalStateException("Card $card has moved away from index $index in zone $zone")
        return value
    }

    fun moveTo(destination: CardZoneI<T>) {
        val card = this.remove()
        destination.add(card)
    }
}

class CardZone<T>(val cards: MutableList<T> = mutableListOf()): CardZoneI<T> {

    override var name: String? = null

    operator fun get(index: Int): Card<T> {
        return Card(this, index, cards[index])
    }

    fun forEach(action: (T) -> Unit) {
        cards.forEach(action)
    }

    fun <R> map(transform: (T) -> R): List<R> {
        return cards.map(transform)
    }

    fun toList(): List<T> = cards.toList()

    fun deal(cards: List<T>, destinations: List<CardZone<T>>) {
        repeat(cards.size) {
            val destination = destinations[it % destinations.size]
            card(cards[it]).moveTo(destination)
        }
    }

    fun <S> findState(state: S, matcher: (T) -> S) = this.cards.find { matcher(it) == state } ?:
        throw IllegalStateException("Card not found for $state. Available cards are ${cards.map(matcher)}")

    fun <S> findStates(states: List<S>, matcher: (T) -> S): List<T> {
        val cards = this.cards.toMutableList()
        val results = mutableListOf<T>()
        for (state in states) {
            val cardIndex = cards.indexOfFirst { matcher(it) == state }
            results.add(cards.removeAt(cardIndex))
        }
        return results
    }

    fun top(i: Int): List<T> = this.cards.take(i)

    override fun card(value: T): Card<T> {
        val index = this.cards.indexOf(value)
        if (index < 0) throw IllegalStateException("card $value is not in zone $this")
        return Card(this, index, value)
    }

    fun first(count: Int, filter: (T) -> Boolean): List<T> {
        val filtered = this.cards.filter(filter)
        if (filtered.size < count) {
            throw IllegalStateException("CardZone contains less than requested cards: ${filtered.size} < $count")
        }
        return filtered.take(count)
    }

    override val size: Int get() = cards.size
    override val indices: IntRange get() = cards.indices

    override fun isEmpty(): Boolean = size == 0
    override fun toString(): String {
        return name ?: super.toString()
    }

    fun asSequence(): Sequence<Card<T>> = this.cards.toList().asSequence().map { card(it) }
    override fun remove(card: Card<T>): T {
        val value = this.cards.removeAt(card.index)
        check(value == card.card) { "Card $card has moved away from index ${card.index} in zone $this" }
        return value
    }

    override fun add(card: T) {
        this.cards.add(card)
    }

    fun moveAllTo(destination: CardZoneI<T>) {
        while (this.cards.isNotEmpty()) {
            this.card(this.cards.first()).moveTo(destination)
        }
    }

    fun random(replayable: ReplayableScope, count: Int, stateKey: String, matcher: (T) -> String): Sequence<Card<T>> {
        if (count == 0) return emptySequence()
        require(count <= this.size) { "Requesting more cards $count than what exists in zone $size" }
        return replayable.randomFromList(stateKey, this.cards, count, matcher).asSequence().map { card(it) }
    }
    fun randomWithRefill(refill: CardZone<T>, replayable: ReplayableScope, count: Int, stateKey: String, matcher: (T) -> String): Sequence<Card<T>> {
        if (count == 0) return emptySequence()
        if (count <= this.size) {
            return random(replayable, count, stateKey, matcher)
        }
        val oldSize = this.size
        val seq = cards.toList().map { card(it) }.asSequence()
        refill.asSequence().forEach { it.moveTo(this) }
        val refilled = refill.random(replayable, count - oldSize, stateKey, matcher)
        return seq + refilled
    }

    override fun isNotEmpty(): Boolean = !isEmpty()

}
fun <T: Replayable> CardZone<T>.random(replayable: ReplayableScope, count: Int, stateKey: String): Sequence<Card<T>> {
    return this.random(replayable, count, stateKey) { it.toStateString() }
}
