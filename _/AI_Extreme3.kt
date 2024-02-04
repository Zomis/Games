package net.zomis.minesweeper.ais

import net.tejpbit.ais.TejpbitAI_Hard
import net.zomis.minesweeper.aiscore.*

// @AI(rating = 2500)
class AI_Extreme3 : AI_Zomis(
    "#AI_Extreme3", ScoreConfigFactory(AnalyzeMethod.ZOMIS_ADVANCED)
        .withScorer(MineProbability(), 1)
        .withScorer(NonRevealingSafeClicks())
        .withScorer(CertainMines())
        .withScorer(OpenFieldsPenalty())
        .withScorer(SafeProbabilityAroundMine())
        .withScorer(SafeWithRiskAroundMineZomis())
        .withScorer(SafeOpenField())
        .withScorer(UnevenProbabilities())
        .withScorer(AvoidReveal50_New())
        .withScorer(BombScore())
        .withScorer(BombIfManyMinesScorer(8.0 / 51.0))
        .build()
) {
    init {
        this.setBackup(StaticAISupplier(TejpbitAI_Hard()))
    }

    fun play(pp: MinesweeperPlayingPlayer?): MinesweeperMove {
        return this.playWithDrawproposal(pp)
    }
}