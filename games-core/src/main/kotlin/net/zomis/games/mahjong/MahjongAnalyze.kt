package net.zomis.games.mahjong

object MahjongAnalyze {

    data class MahjongTiles(val hand: List<MahjongTile>)
    enum class MahjongGroupType { CHOW, PONG, KONG, MAHJONG }
    data class MahjongGroupedTiles(val tiles: List<MahjongTile>, val type: MahjongGroupType)

    enum class MahjongHand(condition: (MahjongTiles) -> Boolean) {
//        MIXED_SHIFTED_STRAIGHT({ tiles ->
//
//        })
    }

    fun findChows(hand: MahjongTiles): Set<MahjongGroupedTiles> {
        val r = MahjongSuit.numberSuits().map outer@{ suit ->
            val filtered = hand.hand.filter { it.suit == suit }
            val matches: List<MahjongGroupedTiles> = (1..7).map { value ->
                val one = filtered.find { it.value == value }
                val two = filtered.find { it.value == value + 1 }
                val three = filtered.find { it.value == value + 2 }
                val result: MahjongGroupedTiles? = if (one != null && two != null && three != null) MahjongGroupedTiles(listOf(one, two, three), MahjongGroupType.CHOW) else null
                return@map result
            }.filterNotNull()
            return@outer emptySet<MahjongGroupedTiles>()
        }
        return emptySet()
    }

    fun findPongs(hand: MahjongTiles): Set<MahjongGroupedTiles> {
        return emptySet()
    }

    fun evaluate(hand: List<MahjongTile>): Set<MahjongHand> {
        return emptySet()
    }

    fun analyze(tiles: List<MahjongTile>): Map<MahjongHand, Double> {
        return emptyMap()
    }


}