package net.zomis.games

import net.zomis.games.api.Games
import net.zomis.games.components.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import kotlin.random.Random

class Map2DTest {

    @Test
    fun testFixed() {
        val map = GridImpl(3, 3) { x, y -> y * 3 + x}
        Assertions.assertEquals(Transformation.ROTATE_180, map.standardizedTransformation { it })
        assertMapValues(map) {
            row(0, 1, 2)
            row(3, 4, 5)
            row(6, 7, 8)
        }

        map.transform(Transformation.ROTATE_90)
        assertMapValues(map) {
            row(6, 3, 0)
            row(7, 4, 1)
            row(8, 5, 2)
        }
    }

    class MapAssertion<T>(val map: Grid<T>) {
        var position: Position? = Position(0, 0, map.sizeX, map.sizeY)
        fun row(vararg values: T) {
            Assertions.assertEquals(map.sizeX, values.size)
            values.forEach {
                Assertions.assertNotNull(position)
                Assertions.assertEquals(it, map.get(position!!.x, position!!.y))
                position = position?.next()
            }
        }
    }
    private fun <T> assertMapValues(map: Grid<T>, assertions: MapAssertion<T>.() -> Unit) {
        val assertion = MapAssertion(map)
        assertions(assertion)
        Assertions.assertNull(assertion.position)
    }

    @Test
    fun transformationMapping() {
        val transformations = Transformation.values().toList()
        val rand = Random(43)
        val map = Games.components.grid(5, 5) { _, _ ->
            rand.nextInt(1000)
        }

        val combinations = transformations.flatMap { a -> transformations.map { b -> a to b } }
        combinations.forEach {
            (0 until map.sizeY).forEach {y ->
                (0 until map.sizeX).forEach {x ->
                    val pos = Position(x, y, map.sizeX, map.sizeY)
                    Assertions.assertEquals(it.second.transform(it.first.transform(pos)), it.first.apply(it.second).transform(pos))
                }
            }
        }
    }

    @Test
    fun expandable() {
        val map = Games.components.expandableGrid<String>()
        map.set(0, 0, "center")
        Assertions.assertEquals("center", map.get(0, 0))
        map.set(1, 0, "right")
        map.set(2, 0, "right2")
        map.set(0, -1, "up")
        Assertions.assertEquals(3, map.sizeX)
        Assertions.assertEquals(2, map.sizeY)

        val border = map.border()
        Assertions.assertEquals(0, border.left)
        Assertions.assertEquals(-1, border.top)
        Assertions.assertEquals(2, border.right)
        Assertions.assertEquals(0, border.bottom)
        Assertions.assertEquals(3, border.width())
        Assertions.assertEquals(2, border.height())

        val view = map.view { it }
        Assertions.assertEquals(0, view["left"])
        Assertions.assertEquals(-1, view["top"])
        Assertions.assertEquals(3, view["width"])
        Assertions.assertEquals(2, view["height"])
        val grid = view["grid"] as List<List<String>>
        Assertions.assertEquals("up", grid[0][0])
        Assertions.assertEquals(null, grid[0][1])
        Assertions.assertEquals(null, grid[0][2])

        Assertions.assertEquals("center", grid[1][0])
        Assertions.assertEquals("right", grid[1][1])
        Assertions.assertEquals("right2", grid[1][2])
    }

}