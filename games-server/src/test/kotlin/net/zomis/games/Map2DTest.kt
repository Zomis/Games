package net.zomis.games

import org.junit.jupiter.api.Test
import kotlin.random.Random

class Map2DTest {

    @Test
    fun test() {


        val rand = Random(43)
        val map = Map2DX<Int>(5, 5) {x, y ->
            rand.nextInt(3)// y * 3 + x
        }
        println(map)

        println(map.standardize { it })
    }

}