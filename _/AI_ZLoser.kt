package net.zomis.minesweeper.ais

import net.zomis.minesweeper.ais.otherscorers.StaticScoreForWeapon

// @AI(rating = -99999)
class AI_ZLoser : AI_Zomis(
    "#AI_ZLoser", ScoreConfigFactory(AnalyzeMethod.ZOMIS_BASIC) // Test scorers
        .withScorer(MineProbability(), -1)
        .withScorer(StaticScoreForWeapon(MinesweeperMove.STANDARD_BOMB, -1000))
        .build()
) {
    init {
        this.hide()
    }

    fun play(pp: MinesweeperPlayingPlayer): MinesweeperMove {
        if (this.agreeDraw(pp)) {
            if (pp.proposeDraw()) {
                this.sendChatMessage(pp, "I propose draw! You've got 10 seconds to agree or not")
                if (!pp.isEliminated()) {
                    try {
                        java.lang.Thread.sleep(10000)
                    } catch (e: InterruptedException) {
                    }
                }
                if (!pp.isEliminated()) this.sendChatMessage(pp, "10 seconds has passed, now I make my move.")
            }
        }
        return super.play(pp)
    }
}