package net.zomis.games.ecsmodel

fun interface KnownMap {
    object Public : KnownMap {
        override fun isKnownTo(playerIndex: Int): Boolean = true
    }
    data class Private(val knownTo: Int) : KnownMap {
        override fun isKnownTo(playerIndex: Int): Boolean = playerIndex == knownTo
    }

    fun isKnownTo(playerIndex: Int): Boolean
    fun and(other: KnownMap): KnownMap = CombinedAnd(this, other)

}

internal class CombinedAnd(val a: KnownMap, val b: KnownMap) : KnownMap {
    override fun isKnownTo(playerIndex: Int): Boolean = a.isKnownTo(playerIndex) && b.isKnownTo(playerIndex)
}

internal class KnownMapList : KnownMap {
    val known = mutableListOf<Int>()

    override fun isKnownTo(playerIndex: Int): Boolean = known.contains(playerIndex)
}