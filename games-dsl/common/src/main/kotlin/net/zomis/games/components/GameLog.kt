package net.zomis.games.components

/**
 * Log mostly used for action history and for other things that players might be interested in.
 *
 * Used to be handled separately from regular view objects but was made into a class for the following reasons:
 * - Games may show the log anywhere they want, it shouldn't be fixed by the client
 * - Some games want to remove older log items (Memory and other games where only last round is known, many social deduction games)
 * - Games should properly handle showing all the different kinds of logs,
 * and having a type-based system for that makes it a lot simpler
 * and doesn't require any ugly special-handling which was used in the past.
 */
class GameLog<T> {
    private val items = mutableListOf<T>()

    fun add(value: T) {
        items.add(value)
    }
    fun clearBeforeLast(condition: (T) -> Boolean) {
        val index = items.indexOfLast(condition)
        repeat(index) { items.removeAt(0) }
    }

    fun private(playerIndex: Int, item: T): PrivateLog<T> {
        return PrivateLog(playerIndex, item, null)
    }
}
class PrivateLog<T>(val playerIndex: Int, val item: T, val publicValue: T?) {
    fun public(publicValue: T) = PrivateLog(playerIndex, item, publicValue)
}
