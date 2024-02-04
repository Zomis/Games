package net.zomis.minesweeper.aiscore

import net.zomis.minesweeper.game.Flags.Field

class MineProbability : AbstractScorer() {
    fun getScoreFor(
        field: Flags.Field?,
        data: ProbabilityKnowledge<Flags.Field?>?,
        scores: ScoreParameters
    ): Double {
        val grp: FieldGroup<Flags.Field> = scores.getAnalyze().getAnalyze().getGroupFor(field) ?: return 0
        return grp.probability
    }

    fun workWithWeapon(scores: ScoreParameters): Boolean {
        return this.weaponIsClick(scores.getWeapon())
    }
}