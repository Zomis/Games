package net.zomis.games.common

import net.zomis.games.PlayerEliminationsRead
import kotlin.math.pow
import kotlin.math.round

typealias PlayerIndex = Int?
fun PlayerIndex.isObserver(): Boolean = this == null
object Players {
    fun startingWith(playerIndices: List<Int>, firstPlayer: Int): List<Int> = playerIndices.shifted(firstPlayer)
}

infix fun Int.fmod(other: Int) = ((this % other) + other) % other
fun Int.withLeadingZeros(minSize: Int): String {
    return '0'.toString().repeat(minSize - this.toString().length) +
        this.toString()
}
fun Int.next(playerCount: Int): Int = (this + 1) % playerCount
@Deprecated("playerCount parameter not required")
fun Int.next(playerCount: Int, eliminations: PlayerEliminationsRead): Int
    = this.next(eliminations)
fun Int.next(eliminations: PlayerEliminationsRead): Int {
    if (eliminations.isGameOver()) throw IllegalStateException("Game is over, next player cannot happen.")
    var next: Int
    do {
        next = (this + 1) % eliminations.playerCount
    } while (eliminations.isEliminated(next))
    return next
}
fun Int.next(playerCount: Int, accept: (Int) -> Boolean): Int? {
    for (attempt in 1..playerCount) {
        val next = (this + attempt) % playerCount
        if (accept.invoke(next)) {
            return next
        }
    }
    return null
}
fun Int.nextReversed(playerCount: Int): Int = (this - 1 + playerCount) % playerCount

fun Double.toPercent(decimals: Int): String {
    val power = (10.0).pow(decimals)
    val value = round(this * power * 100) / power
    return value.toString()
}

class CurrentPlayer<T>(val players: List<T>) {
    var index: Int = 0

    var player: T
        get() = players[index]
        set(value) { index = players.indexOf(value) }

    fun changeBy(offset: Int) {
        this.index = (this.index + offset + players.size) % players.size
    }

    fun next() = changeBy(1)
    fun getOffset(offset: Int): T {
        val index = (this.index + offset + players.size) % players.size
        return players[index]
    }

    fun nextMatching(filter: (T) -> Boolean) {
        do { changeBy(1) } while (!filter(player))
    }

}