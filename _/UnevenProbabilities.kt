package net.zomis.minesweeper.aiscore

import net.zomis.minesweeper.game.Flags.Field

class UnevenProbabilities : AbstractScorer() {
    fun getScoreFor(
        field: Flags.Field?,
        data: ProbabilityKnowledge<Flags.Field?>, scores: ScoreParameters?
    ): Double {
        for (dd in data.probabilities) {
            if (dd != 0.0 && dd != 0.5) return -0.00001 // This is mostly useful at the end of a game, and to fix a mistake by MarioEV
        }
        return 0
    }

    fun workWithWeapon(scores: ScoreParameters): Boolean {
        return this.weaponIsClick(scores.getWeapon())
    }
}