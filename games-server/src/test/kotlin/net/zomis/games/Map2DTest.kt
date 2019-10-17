package net.zomis.games

import org.junit.jupiter.api.Test
import kotlin.random.Random

class Map2DTest {

    @Test
    fun test() {
        val rand = Random.Default
        val map = Map2D<Int>(3, 3) {x, y ->
            rand.nextInt(3)// y * 3 + x
        }
        println(map)

        println(map.standardize { it })
    }

}