package net.zomis.games.impl.minesweeper.ais

import net.zomis.games.impl.minesweeper.Flags
import net.zomis.games.impl.minesweeper.Weapon
import net.zomis.games.scorers.ScorerFactory

object StaticScoreForWeapon {

    fun scorer(scorers: ScorerFactory<Flags.Model>, function: (Weapon) -> Boolean) = run {
        scorers.action(Flags.useWeapon) {
            if (function.invoke(action.parameter.weapon)) 1.0 else 0.0
        }
    }
}