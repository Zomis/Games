package net.zomis.minesweeper.aiscore

import net.zomis.minesweeper.analyze.utils.OpenFieldApproxer

class NonRevealingSafeClicks : AbstractScorer() {
    private val openFieldScan: OpenFieldApproxer = OpenFieldApproxer()
    fun getScoreFor(
        field: Flags.Field?,
        data: ProbabilityKnowledge<Flags.Field?>, scores: ScoreParameters
    ): Double {
        return if (isInterestingField(field, data, scores)) 0.01 else 0
    }

    fun isInterestingField(
        field: Flags.Field?,
        data: ProbabilityKnowledge<Flags.Field?>,
        scores: ScoreParameters
    ): Boolean {
        for (i in data.probabilities.indices) if (data.probabilities.get(i) == 1.0) {
            return if (i == 0) {
                // Safe open field
                openFieldScan.floodFill(scores.getAnalyze(), field) === 0
            } else true
        }
        return false
    }

    fun workWithWeapon(scores: ScoreParameters): Boolean {
        return this.weaponIsClick(scores.getWeapon())
    }
}