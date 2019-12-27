package net.zomis.games.ais

import net.zomis.games.server2.games.ttt3d.TTT3DIO
import net.zomis.games.server2.games.ttt3d.loadMap
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class AlphaBetaTest {

    @Test
    fun test() {
        val map = loadMap("XXXO|OOXX|XXOO|XXOO / XOOX|OXO |XOXO|XXXO /      | X    |      |      / OXOX | XOOO | OOXO | OXOX");
        val io = TTT3DIO()
        io.print(map)
        io.printScores(map, io.factory)
        val move = io.alphaBeta(map, 6)
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