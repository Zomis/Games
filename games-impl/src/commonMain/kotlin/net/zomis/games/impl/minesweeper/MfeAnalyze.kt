package net.zomis.games.impl.minesweeper

import net.zomis.minesweeper.analyze.detail.NeighborFind
import net.zomis.minesweeper.analyze.factory.AbstractAnalyze
import net.zomis.minesweeper.analyze.factory.General2DAnalyze

class MfeAnalyze private constructor(val game: Flags.Model) : AbstractAnalyze<Flags.Field>() {

    override val allPoints: List<Flags.Field> = game.grid.all().map { it.value }
    override val remainingMinesCount: Int = game.remainingMines()
    override val allUnclickedFields: List<Flags.Field> = allPoints.filter { !it.clicked }

    override fun isClicked(field: Flags.Field): Boolean = field.blocked || field.clicked

    override fun getNeighbors(field: Flags.Field): List<Flags.Field> = field.neighbors

    override fun getFieldValue(field: Flags.Field): Int = field.value

    override fun getNeighborsFor(field: Flags.Field): Collection<Flags.Field> = field.neighbors

    override fun isFoundAndisMine(field: Flags.Field): Boolean = field.isDiscoveredMine()

    override fun isDiscoveredMine(field: Flags.Field): Boolean = field.isMine() && field.clicked

    override fun fieldHasRule(field: Flags.Field): Boolean {
        return !field.blocked && isClicked(field) && !isDiscoveredMine(field)
    }

    object NeighborStrategy: NeighborFind<Flags.Field> {
        override fun getNeighborsFor(field: Flags.Field): Collection<Flags.Field> {
            return field.neighbors
        }

        override fun isFoundAndisMine(field: Flags.Field): Boolean {
            return field.isMine() && field.clicked
        }
    }

    companion object {
        fun analyze(game: Flags.Model) = MfeAnalyze(game).also { it.createRules() }.solve()
    }

}