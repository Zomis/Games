package net.zomis.games.server2

import net.zomis.games.components.Point
import net.zomis.games.impl.grids.Ricochet
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

@OptIn(ExperimentalTime::class)
object RicochetTest {
    fun random() {
        val map = Ricochet.createMap()
//        map.shuffleBoards()
//        map.shufflePieces()
//        println(map.toString())
        map.place(Ricochet.Color.Red.starSign, Point(11, 3))
        map.place(Ricochet.Color.Red.cog, Point(3, 14))
        map.place(Ricochet.Color.Red.planet, Point(2, 4))
        map.place(Ricochet.Color.Red.halfMoon, Point(14, 11))

        map.piece(Ricochet.Color.Red, Point(3, 2))
        map.piece(Ricochet.Color.Yellow, Point(15, 4))
        map.piece(Ricochet.Color.Green, Point(1, 11))
        map.piece(Ricochet.Color.Blue, Point(11, 9))
        map.piece(Ricochet.Color.Wildcard, Point(14, 15))

        println(map.toString())


        findAll(map)
    }

    private fun findAll(map: Ricochet.GameMap) {
        Ricochet.targets().forEach {
            val path = measureTimedValue {
                map.findPath(it)
            }
            println("Target $it: ${path.value.size} (${path.duration})")
            println(path.value)
            println()
        }
    }

    fun ownTest() {
        val map = Ricochet.createMap()
        map.place(Ricochet.Color.Yellow.halfMoon, Point(13, 3))
        map.place(Ricochet.Color.Green.cog, Point(1, 12))
        map.place(Ricochet.Color.Red.starSign, Point(3, 4))
        map.place(Ricochet.Color.Green.halfMoon, Point(11, 14))

        map.piece(Ricochet.Color.Red, Point(1, 6))
        map.piece(Ricochet.Color.Yellow, Point(11, 3))
        map.piece(Ricochet.Color.Green, Point(10, 4))
        map.piece(Ricochet.Color.Blue, Point(5, 1))
        map.piece(Ricochet.Color.Wildcard, Point(9, 12))

        println(map.toString())
        findAll(map)
    }
    fun messengerPic() {
        val map = Ricochet.createMap()
        map.place(Ricochet.Color.Yellow.halfMoon, Point(3, 2))
        map.place(Ricochet.Color.Green.cog, Point(14, 3))
        map.place(Ricochet.Color.Red.starSign, Point(4, 12))
        map.place(Ricochet.Color.Green.halfMoon, Point(11, 14))

        map.piece(Ricochet.Color.Red, Point(11, 1))
        map.piece(Ricochet.Color.Yellow, Point(11, 10))
        map.piece(Ricochet.Color.Green, Point(5, 9))
        map.piece(Ricochet.Color.Blue, Point(10, 10))
        map.piece(Ricochet.Color.Wildcard, Point(3, 7))

        println(map.toString())

        var path = map.findPath(Ricochet.Color.Yellow.halfMoon) // Two: Yellow UP, LEFT
        check(path == Ricochet.Color.Yellow.up + Ricochet.Color.Yellow.left)
        path = map.findPath(Ricochet.Color.Blue.planet) // Three: Blue UP, LEFT, DOWN
        check(path == Ricochet.Color.Blue.up + Ricochet.Color.Blue.left + Ricochet.Color.Blue.down)
        path = map.findPath(Ricochet.Color.Green.cog) // Four: Yellow UP. Green RIGHT, UP, RIGHT
        check(path == Ricochet.Color.Yellow.up + Ricochet.Color.Green.right + Ricochet.Color.Green.up + Ricochet.Color.Green.right)

        findAll(map)
    }

}
