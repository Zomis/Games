package net.zomis.games.impl.minesweeper.ais

import net.zomis.games.impl.minesweeper.Flags
import net.zomis.games.impl.minesweeper.MfeProbabilityProvider
import net.zomis.games.impl.minesweeper.SizedBombWeapon
import net.zomis.games.scorers.Scorer
import net.zomis.games.scorers.ScorerFactory

object BombIfManyMinesScorer {

    fun scorer(
        threshold: Double,
        scorers: ScorerFactory<Flags.Model>,
        analysis: MfeProbabilityProvider,
    ): Scorer<Flags.Model, Any> = run {
        scorers.action(Flags.useWeapon) {
            if (action.parameter.weapon !is SizedBombWeapon) return@action 0.0
//        if (weaponIsClick(scores.getWeapon())) { // For MfeFrame, to see exact expected bomb result.
//            return BombTools.getBombProbability(field, scores.getAnalyze().getAnalyze())
//        }
            val analyseResult = require(analysis)!!
            if (MineprobHelper.find100(analyseResult) > 0) return@action 0.0 // Don't bomb if there are 100% mines out there
            val player = action.game.players[action.playerIndex]
            val affected: Collection<Flags.Field> = action.parameter.weapon.affectedArea(action.game, player.playerIndex, action.parameter.position).map { action.game.fieldAt(it) }

            // If it is not time, you need to return a lot of negative so that it is not higher than the score for a normal click
            if (affected.isEmpty()) return@action -10000.0

            val dd: Double = BombTools.getBombProbability(affected, analyseResult)
            (if (dd >= threshold * action.game.totalMines()) 1000.0 else -1000.0) + dd
        }
    }

}