package net.zomis.games.common

typealias PlayerIndex = Int?
fun PlayerIndex.isObserver(): Boolean = this == null

fun Int.next(playerCount: Int): Int = (this + 1) % playerCount
