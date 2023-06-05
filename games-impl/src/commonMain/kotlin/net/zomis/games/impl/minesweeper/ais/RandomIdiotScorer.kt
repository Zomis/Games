package net.zomis.games.impl.minesweeper.ais

import net.zomis.games.impl.minesweeper.Flags
import net.zomis.games.impl.minesweeper.SizedBombWeapon
import net.zomis.games.impl.minesweeper.Weapon
import net.zomis.games.scorers.Scorer
import net.zomis.games.scorers.ScorerFactory
import kotlin.random.Random

object RandomIdiotScorer {

    fun workWithWeapon(model: Flags.Model, playerIndex: Int, weapon: Weapon): Boolean {
        return if (weapon is SizedBombWeapon) {
            for (w in model.players[playerIndex].weapons) {
                if (w !is SizedBombWeapon && w.usableForPlayer(model, playerIndex)) return false
            }
            true
        } else true
    }

    fun scorer(scorers: ScorerFactory<Flags.Model>): Scorer<Flags.Model, Any> = run {
        scorers.action(Flags.useWeapon) {
            if (!workWithWeapon(action.game, action.playerIndex, action.parameter.weapon)) return@action 0.0
            Random.Default.nextDouble()
        }
    }
}