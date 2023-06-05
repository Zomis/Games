package net.zomis.games.impl.minesweeper.ais

import net.zomis.games.impl.minesweeper.Flags
import net.zomis.games.impl.minesweeper.MfeProbabilityProvider
import net.zomis.games.impl.minesweeper.SizedBombWeapon
import net.zomis.games.scorers.Scorer
import net.zomis.games.scorers.ScorerFactory

object BombScore {

    fun scorer(factory: ScorerFactory<Flags.Model>, provider: MfeProbabilityProvider): Scorer<Flags.Model, Any> {

        return factory.action(Flags.useWeapon) {
            if (action.parameter.weapon !is SizedBombWeapon) return@action 0.0

            val player = action.game.players[action.playerIndex]
            val affected = action.parameter.weapon.affectedArea(action.game, action.playerIndex, action.parameter.position)
            if (affected.isEmpty()) return@action -10000.0

            val bombProbability = BombTools.getBombProbability(affected.map { action.game.fieldAt(it) }, require(provider)!!)
            return@action (if (player.score + bombProbability >= action.game.totalMines() / 2.0) 100000 else -100) + bombProbability
            // TODO: Calculate number of mines needed to win, for 3+ player games.
            // If it is not time, you need to return a lot of negative so that it is not higher than the score for a normal click
        }
    }

}