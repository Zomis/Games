package net.zomis.games.cards

import net.zomis.games.dsl.Replayable
import net.zomis.games.dsl.ReplayableScope

data class Card<T>(val zone: CardZone<T>, val index: Int, val card: T) {
    fun moveAndReplace(moveTo: CardZone<T>, replaceWith: Card<T>): T {
        moveTo.cards.add(this.zone.cards[index])
        val newCard = replaceWith.remove()
        this.zone.cards[index] = newCard
        return newCard
    }

    fun remove(): T {
        val value = this.zone.cards.removeAt(this.index)
        if (value != this.card) throw IllegalStateException("Card $card has moved away from index $index in zone $zone")
        return value
    }

    fun moveTo(destination: CardZone<T>) {
        val card = this.remove()
        destination.cards.add(card)
    }
}

class CardZone<T>(internal val cards: MutableList<T> = mutableListOf()) {

    var name: String? = null

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

    fun card(card: T): Card<T> {
        val index = this.cards.indexOf(card)
        if (index < 0) throw IllegalStateException("card $card is not in zone $this")
        return Card(this, index, card)
    }

    fun first(count: Int, filter: (T) -> Boolean): List<T> {
        val filtered = this.cards.filter(filter)
        if (filtered.size < count) {
            throw IllegalStateException("CardZone contains less than requested cards: ${filtered.size} < $count")
        }
        return filtered.take(count)
    }

    val size: Int get() = cards.size
    val indices: IntRange get() = cards.indices

    override fun toString(): String {
        return name ?: super.toString()
    }

    fun asSequence(): Sequence<Card<T>> = this.cards.toList().asSequence().map { card(it) }

    fun moveAllTo(destination: CardZone<T>) {
        while (this.cards.isNotEmpty()) {
            this.card(this.cards.first()).moveTo(destination)
        }
    }

    fun random(replayable: ReplayableScope, count: Int, stateKey: String, matcher: (T) -> String): Sequence<Card<T>> {
        require(count <= this.size) { "Requesting more cards $count than what exists in zone $size" }
        return replayable.randomFromList(stateKey, this.cards, count, matcher).asSequence().map { card(it) }
    }

}
fun <T: Replayable> CardZone<T>.random(replayable: ReplayableScope, count: Int, stateKey: String): Sequence<Card<T>> {
    return this.random(replayable, count, stateKey) { it.toStateString() }
}
