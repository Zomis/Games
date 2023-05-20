package net.zomis.games.impl.minesweeper

import net.zomis.games.common.next
import net.zomis.games.components.Point

open class Weapon(val name: String) {
    open fun usableForPlayer(game: Flags.Model, playerIndex: Int): Boolean = game.currentPlayer == playerIndex
    open fun usableAt(game: Flags.Model, playerIndex: Int, field: Flags.Field): Boolean = true
    open fun affectedArea(game: Flags.Model, playerIndex: Int, position: Point): Set<Point> = emptySet()
    open fun use(game: Flags.Model, playerIndex: Int, field: Flags.Field) {}
}

object Weapons {
    const val INFINITE_USAGES = -1

    class Default(val usages: Int = INFINITE_USAGES) : Weapon("default") {
        override fun usableAt(game: Flags.Model, playerIndex: Int, field: Flags.Field): Boolean {
            return field.takenBy == null
        }

        override fun use(game: Flags.Model, playerIndex: Int, field: Flags.Field) {
            reveal(game, playerIndex, field, expand = true)
            if (!field.isMine()) {
                game.nextPlayer()
            }
        }
    }
    class Bomb(val usages: Int = 1) : Weapon("bomb")
    class Laser : Weapon("laser")
    class Sniper : Weapon("sniper")

    fun reveal(game: Flags.Model, playerIndex: Int, field: Flags.Field, expand: Boolean) {
        if (field.clicked) return
        field.reveal(game.players[playerIndex])
        if (expand && field.value == 0 && field.mineValue == 0) {
            field.neighbors.forEach {
                reveal(game, playerIndex, it, expand)
            }
        }
    }
}

class WeaponUse(val weapon: Weapon, val position: Point)
