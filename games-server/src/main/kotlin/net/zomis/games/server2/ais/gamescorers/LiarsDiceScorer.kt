package net.zomis.games.server2.ais.gamescorers

import net.zomis.games.impl.LiarsDice
import net.zomis.games.impl.LiarsDiceGame
import net.zomis.games.server2.ais.ScorerAIFactory
import net.zomis.games.server2.ais.scorers.ScorerFactory

object LiarsDiceScorer {

    val scorers = ScorerFactory<LiarsDice>()

    fun ais() = listOf(
        ScorerAIFactory("LiarsDice", "#AI_Unpredictable_Cheater", cheatingLiar, cheatingSpotOn,
            cheatingBetExact, cheatingBetOneLess, cheatingBetOneMore
        )
    )

    val cheatingLiar = scorers.actionConditional(LiarsDiceGame.liar) { model.isLie() }
    val cheatingSpotOn = scorers.actionConditional(LiarsDiceGame.spotOn) { model.isSpotOn() }
    val cheatingBetExact = cheatingBet(0)
    val cheatingBetOneMore = cheatingBet(1)
    val cheatingBetOneLess = cheatingBet(-1)
    val cheatingBetDiff = scorers.action(LiarsDiceGame.bet) {
        model.correctBet(action.parameter.value).amount - action.parameter.amount.toDouble()
    }
    fun cheatingBet(offBy: Int) = scorers.actionConditional(LiarsDiceGame.bet) {
        model.correctBet(action.parameter.value).amount == action.parameter.amount + offBy
    }

}