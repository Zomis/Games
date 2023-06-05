package net.zomis.games.impl.minesweeper.ais

import net.zomis.games.impl.minesweeper.Flags
import net.zomis.games.impl.minesweeper.Weapons
import net.zomis.games.scorers.ScorerFactory

object NeighborsNeedsMoreMines {

    fun scorer(scorers: ScorerFactory<Flags.Model>) = run {
        scorers.action(Flags.useWeapon) {
            if (action.parameter.weapon !is Weapons.Default) return@action 0.0

            var dd = 0.0
            val field = action.game.fieldAt(action.parameter.position)
            for (ff in field.inverseNeighbors) {
                dd += ZomisTools.fieldNeedsMoreMines(ff)
            }
            return@action dd
        }
    }
}