package net.zomis.games.cards

data class Card<T>(val zone: CardZone<T>, val index: Int, val card: T) {
    fun moveAndReplace(moveTo: CardZone<T>, replaceWith: CardZone<T>): T {
        moveTo.cards.add(this.zone.cards[index])
        val newCard = replaceWith.cards.removeAt(0)
        this.zone.cards[index] = newCard
        return newCard
    }
}

class CardZone<T>(internal val cards: MutableList<T> = mutableListOf()) {

    // cards should be private
    // replace(destination, replacement)
    // moveTo
    // card(filter / object) - moveTo, create Card<T> when needed and set zone

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

    fun deal(count: Int, destinations: List<CardZone<T>>) {
        repeat(count) {
            val destination = destinations[it % destinations.size]
            destination.cards.add(this.cards.removeAt(0))
        }
    }

    val size: Int get() = cards.size
    val indices: IntRange get() = cards.indices

}
