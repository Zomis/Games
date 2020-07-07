package net.zomis.games.server2.ais.gamescorers

import net.zomis.games.dsl.GamesImpl
import net.zomis.games.impl.LiarsDiceGame

object LiarsDiceScorer {

    val scorers = GamesImpl.game(LiarsDiceGame.game).scorers()

    fun ais() = listOf(
        scorers.ai("#AI_Unpredictable_Cheater", cheatingLiar, cheatingSpotOn,
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