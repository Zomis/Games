package net.zomis.games.common

import net.zomis.games.PlayerEliminationCallback
import kotlin.math.pow
import kotlin.math.round

typealias PlayerIndex = Int?
fun PlayerIndex.isObserver(): Boolean = this == null

infix fun Int.fmod(other: Int) = ((this % other) + other) % other
fun Int.withLeadingZeros(minSize: Int): String {
    return '0'.toString().repeat(minSize - this.toString().length) +
        this.toString()
}
fun Int.next(playerCount: Int): Int = (this + 1) % playerCount
fun Int.next(playerCount: Int, eliminations: PlayerEliminationCallback): Int {
    if (eliminations.isGameOver()) throw IllegalStateException("Game is over, next player cannot happen.")
    var next: Int
    do {
        next = (this + 1) % playerCount
    } while (eliminations.isEliminated(next))
    return next
}
fun Int.nextReversed(playerCount: Int): Int = (this - 1 + playerCount) % playerCount

fun Double.toPercent(decimals: Int): String {
    val power = (10.0).pow(decimals)
    val value = round(this * power * 100) / power
    return value.toString()
}
