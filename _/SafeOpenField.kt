package net.zomis.minesweeper.aiscore

import net.zomis.games.impl.minesweeper.ais.ZomisTools

class SafeOpenField : AbstractScorer() {
    fun getScoreFor(
        field: Flags.Field?,
        data: ProbabilityKnowledge<Flags.Field?>, scores: ScoreParameters
    ): Double {
        return if (isInterestingField(field, data, scores.getWeapon())) {
            data.mineProbability / 4
        } else 0
    }

    fun isInterestingField(
        field: Flags.Field?,
        data: ProbabilityKnowledge<Flags.Field?>,
        weapon: MinesweeperWeapon?
    ): Boolean {
        return data.mineProbability > 0 && data.probabilities.get(0) == 0.0 && ZomisTools.isZomisOpenField(field)
    }

    fun workWithWeapon(scores: ScoreParameters): Boolean {
        return if (!this.weaponIsClick(scores.getWeapon())) false else true
        // this.getMario().getBoard().MaxExpectedValue() > 0;
    }
}