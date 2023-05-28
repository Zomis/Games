package net.zomis.games.impl.minesweeper.specials

import net.zomis.games.components.Direction4
import net.zomis.games.components.Point
import net.zomis.games.impl.minesweeper.Flags
import net.zomis.games.impl.minesweeper.Weapon
import net.zomis.games.impl.minesweeper.Weapons

class FloodFillWeapon : Weapon("FloodFill") {

    override fun affectedArea(game: Flags.Model, playerIndex: Int, position: Point): Set<Point> {
        return setOf(position)
    }

    override fun usableAt(game: Flags.Model, playerIndex: Int, field: Flags.Field): Boolean {
        return !field.clicked
    }

    override fun use(game: Flags.Model, playerIndex: Int, field: Flags.Field) {
        val affected = mutableSetOf<Flags.Field>()

        Weapons.recursiveAdd(affected, field) { f ->
            Direction4.values().mapNotNull { dir ->
                game.grid.getOrNull(f.x + dir.deltaX, f.y + dir.deltaY)?.takeIf {
                    val mineMatch = it.isMine() && it.mineValue == f.mineValue
                    val numberMatch = !it.isMine() && it.value == f.value
                    mineMatch || numberMatch
                }
            }
        }
        affected.forEach {
            Weapons.reveal(game, playerIndex, it, expand = false)
        }
    }

}