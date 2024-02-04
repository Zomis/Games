package net.zomis.minesweeper.aiscore

import net.zomis.games.impl.minesweeper.ais.ZomisTools

class SafeProbabilityAroundMine : AbstractScorer() {
    fun getScoreFor(
        field: Flags.Field?,
        data: ProbabilityKnowledge<Flags.Field?>, scores: ScoreParameters
    ): Double {
        if (!isInterestingField(field, data, scores.getWeapon())) return 0
        if (!ZomisTools.isZomisOpenField(field)) return 0 // Modified because of 012a2123a2___1b1-01b33a2aa41_1221-012b3234aa1_1a1_-1122a11a321_111_-1b1111111_______-332_____________-aa1_____________-221__xx______x__-_______________x-__111____111____-x_1a1_1122a32_x_-x_11213a3a5aa4b3-___13a4a43abb__x-1234aa313b432___-2aaaa3214a422___-a4a4211a3a3aa1_x
        val probabilities: DoubleArray = data.probabilities
        for (i in probabilities.indices.reversed()) {
            if (probabilities[i] > 0) {
                val prob: Double = data.mineProbability - probabilities[i] * (i - data.found)
                return prob / 10
            }
        }
        if (data.mineProbability > 0.99) return 0
        throw AssertionError("Something is horribly wrong with these probabilities: $data")
    }

    fun isInterestingField(
        field: Flags.Field?,
        data: ProbabilityKnowledge<Flags.Field?>,
        weapon: MinesweeperWeapon?
    ): Boolean {
        return data.mineProbability > 0 && (data.found > 0 || data.probabilities.get(0) == 0.0)
    }

    fun workWithWeapon(scores: ScoreParameters): Boolean {
        return this.weaponIsClick(scores.getWeapon())
    }
}