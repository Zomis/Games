package net.zomis.games.impl.minesweeper

import net.zomis.games.components.Point
import kotlin.math.floor

class SizedBombWeapon(
    weaponName: String,
    private val bombSize: Int,
    uses: Int = 1,
) : Weapon(weaponName) {
    private var usesRemaining = uses

    override fun affectedArea(game: Flags.Model, playerIndex: Int, position: Point): Set<Point> {

        val realField: Flags.Field = getMove(game, game.fieldAt(position))
        return getFieldsWithinRange(game, realField, rangeDown, rangeUp).filter { !it.clicked }.map {
            Point(it.x, it.y)
        }.toSet()
    }

    override fun usableAt(game: Flags.Model, playerIndex: Int, field: Flags.Field): Boolean {
        val affected: Collection<Any> = affectedArea(game, playerIndex, Point(field.x, field.y))
        return affected.isNotEmpty()
    }

    override fun usableForPlayer(game: Flags.Model, playerIndex: Int): Boolean {
        val maxScore = game.players.maxOf { it.score }
        return usesRemaining != 0 && game.players[playerIndex].score < maxScore && super.usableForPlayer(game, playerIndex)
    }

    override fun use(game: Flags.Model, playerIndex: Int, field: Flags.Field) {
        val fields = affectedArea(game, playerIndex, Point(field.x, field.y))
        fields.forEach {
            val f = game.grid.getOrNull(it) ?: return@forEach
            Weapons.reveal(game, playerIndex, f, expand = true)
        }
        usesRemaining--
        game.nextPlayer()
    }

    protected fun getMove(game: Flags.Model, field: Flags.Field): Flags.Field {
        val xpos: Int = field.x.coerceAtLeast(rangeDown).coerceAtMost(game.grid.sizeX - rangeUp - 1)
        val ypos: Int = field.y.coerceAtLeast(rangeDown).coerceAtMost(game.grid.sizeY - rangeUp - 1)
        return game.grid.get(xpos, ypos)
    }

    private val rangeDown: Int = (bombSize - 1) / 2
    private val rangeUp: Int = floor(bombSize / 2.0).toInt()

    private fun getFieldsWithinRange(
        game: Flags.Model,
        field: Flags.Field,
        rangeDown: Int,
        rangeUp: Int
    ): Collection<Flags.Field> {
        val result = mutableListOf<Flags.Field>()
        for (x in -rangeDown..rangeUp) {
            for (y in -rangeDown..rangeUp) {
                val mf = game.grid.getOrNull(field.x + x, field.y + y)
                if (mf != null) result.add(mf)
            }
        }
        return result
    }

}
