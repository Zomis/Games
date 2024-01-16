package net.zomis.games.ecsmodel

interface KnownMap {
    object Public : KnownMap {
        override fun isKnownTo(playerIndex: Int): Boolean = true
    }

    fun isKnownTo(playerIndex: Int): Boolean
}

class KnownMapList : KnownMap {
    val known = mutableListOf<Int>()

    override fun isKnownTo(playerIndex: Int): Boolean = known.contains(playerIndex)
}