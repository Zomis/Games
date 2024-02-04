package net.zomis.minesweeper.ais

import net.zomis.minesweeper.aiscore.*
// @AI(rating = -99999)
class Disabled_AI_Horrible : AI_Zomis(
    "#AI_DisabledHorrible", ScoreConfigFactory(AnalyzeMethod.ZOMIS_ADVANCED)
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
        .withScorer(BombIfManyMinesScorer(7.0 / 51.0))
        .multiplyAll(-1)
        .build()
)