package net.zomis.minesweeper.aiscore

import net.zomis.minesweeper.game.Flags.Field

class SafeWithRiskAroundMineZomis : AbstractScorer() {
    fun getScoreFor(
        field: Flags.Field,
        data: ProbabilityKnowledge<Flags.Field?>,
        scores: ScoreParameters
    ): Double {
        if (data.mineProbability > 0) return 0
        if (data.probabilities.get(0) == 1.0) return 0 // Another scorer will take care of this situation
        if (data.neighbors.size() == 1) {
            val (key, value) = data.neighbors.entrySet().iterator().next()
            if (value === key.size()) return -0.01
        }
        if (!isInterestingField(field, data, scores.getWeapon())) return (-2).toDouble()
        var total = 0.0
        //		int numWithProb = 0;
        for (neighbor in field.neighbors) {
            if (neighbor.clicked) continue
            val neighborData: ProbabilityKnowledge<Flags.Field> =
                scores.getAnalyze().getKnowledgeFor(neighbor) ?: continue
            //			if (neighborData.getMineProbability() > 0) numWithProb++;


//			if (neighborData.getProbabilities()[0] > 0 && neighborData.getFieldsInGroup().size() > 8) // Only check open field group basically.
            total += neighborData.mineProbability
            //			else total += 0.333;
        }
        return -total * 0.1 // * 0.1
    }

    // My turn and even number of moves: I will be the one who have to take a risk.
    fun isInterestingField(
        field: Flags.Field?,
        data: ProbabilityKnowledge<Flags.Field?>,
        weapon: MinesweeperWeapon?
    ): Boolean {
        return data.probabilities.get(0) == 0.0 && data.mineProbability == 0.0
    }

    fun workWithWeapon(scores: ScoreParameters): Boolean {
        return this.weaponIsClick(scores.getWeapon())
    }
}