package net.zomis.games.ais

import net.zomis.games.server2.TTT3D
import net.zomis.games.server2.TTT3DIO
import net.zomis.games.server2.TTT3DPiece
import net.zomis.games.server2.loadMap
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import kotlin.streams.toList

class AlphaBetaTest {

    @Test
    fun test() {
        val map = loadMap("XXXO|OOXX|XXOO|XXOO / XOOX|OXO |XOXO|XXXO /      | X    |      |      / OXOX | XOOO | OOXO | OXOX");
        val io = TTT3DIO(map)
        io.print()
        io.printScores(io.factory)
        val move = io.alphaBeta()
        assertNotEquals(0, move.x)
    }
/*
-----------------------------
| XXXO | OOXX | XXOO | XXOO |
-----------------------------
| XOOX | OXO  | XOXO | XXXO |
-----------------------------
|      | X    |      |      |
-----------------------------
| OXOX | XOOO | OOXO | OXOX |
-----------------------------
*/


}