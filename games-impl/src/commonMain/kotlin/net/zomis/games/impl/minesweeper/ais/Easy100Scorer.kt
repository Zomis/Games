package net.zomis.games.impl.minesweeper.ais

import net.zomis.games.impl.minesweeper.Flags
import net.zomis.games.impl.minesweeper.Weapons
import net.zomis.games.scorers.ScorerFactory

object Easy100Scorer {

    private fun unclickedNeighbors(field: Flags.Field): Int {
        var i = 0
        for (ff in field.neighbors) if (!ff.clicked) i++
        return i
    }

    fun scorer(scorers: ScorerFactory<Flags.Model>) = run {
        scorers.action(Flags.useWeapon) {
            if (action.parameter.weapon !is Weapons.Default) return@action 0.0
            val field = action.game.fieldAt(action.parameter.position)

            for (inv in field.inverseNeighbors) {
                if (unclickedNeighbors(inv) == ZomisTools.fieldNeedsMoreMines(inv)) {
                    return@action 1.0
                }
            }
            0.0
        }
    }
}