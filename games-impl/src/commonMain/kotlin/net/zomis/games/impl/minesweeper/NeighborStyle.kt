package net.zomis.games.impl.minesweeper

import net.zomis.games.components.Direction4
import net.zomis.games.components.Direction8
import net.zomis.games.components.Point

enum class NeighborStyle(val neighbors: Set<Point>) {
    NORMAL(Direction8.values().map { it.delta() }.toSet()),
    CHESS_KNIGHT(
        setOf(
            Point(-2, -1), Point(-2, 1),
            Point(-1, -2), Point(-1, 2),
            Point( 1, -2), Point( 1, 2),
            Point( 2, -1), Point( 2, 1),
        )
    ),
    SINGLE_LINE(Direction4.values().map { it.delta() }.toSet()),
    DOUBLE_LINE(SINGLE_LINE.neighbors + SINGLE_LINE.neighbors.map { it * 2 }),
}

object Neighbors {

    fun configure(game: Flags.Model, style: NeighborStyle) {
        game.grid.all().forEach { field ->
            style.neighbors.forEach { offset ->
                val other = field.point + offset
                game.grid.getOrNull(other)?.also { field.value.addMutualNeighbor(it) }
            }
        }
    }

}