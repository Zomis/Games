package net.zomis.games.mahjong

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MahjongTest {

    @Test
    fun test() {
        val mahjong = Mahjong()
        mahjong.startGame()

        assertEquals(14, mahjong.currentPlayer().tiles.size)


        mahjong.players[0].tiles.forEach { println(it) }
    }

    @Test
    fun mixedShiftedTiles() {
        val tiles = MahjongSuit.CIRCLE.createTiles(2, 3, 4)
            .plus(MahjongSuit.BEAN.createTiles(3, 4, 5))
            .plus(MahjongSuit.SIGN.createTiles(4, 5, 6))
//        assertEquals(setOf(MahjongAnalyze.MahjongHand.MIXED_SHIFTED_STRAIGHT), MahjongAnalyze.evaluate(tiles))
    }



}