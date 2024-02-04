package net.zomis.minesweeper.aiscore

import net.zomis.minesweeper.game.Flags.Field

class CertainMines : AbstractScorer() {
    fun getScoreFor(
        field: Flags.Field?,
        data: ProbabilityKnowledge<Flags.Field?>?, scores: ScoreParameters?
    ): Double {
        if (data == null) return 0
        return if (data.mineProbability >= MINE_THRESHOLD) data.mineProbability * POWER else 0
    }

    fun workWithWeapon(scores: ScoreParameters): Boolean {
        return this.weaponIsClick(scores.getWeapon())
    }

    companion object {
        private const val MINE_THRESHOLD = 0.9999
        var POWER = 100.0 // changed from 10 to 100 because of game 1062693
    }
}