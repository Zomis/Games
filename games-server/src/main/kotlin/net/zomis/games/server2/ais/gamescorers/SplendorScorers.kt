package net.zomis.games.server2.ais.gamescorers

import net.zomis.games.impl.*
import net.zomis.games.server2.ais.ScorerAIFactory
import net.zomis.games.server2.ais.scorers.Scorer
import net.zomis.games.server2.ais.scorers.ScorerFactory

object SplendorScorers {

    val scorers = ScorerFactory<SplendorGame>()

    val buyCard = scorers.conditional { action.actionType == DslSplendor.buy.name }
    val buyReserved = scorers.conditional { action.actionType == DslSplendor.buyReserved.name }
    val takeMoneyNeeded = scorers.conditionalType(MoneyChoice::class) {
        1.0
    } as Scorer<SplendorGame, Any>

    val reserve = scorers.conditional { action.actionType == DslSplendor.reserve.name }

    val aiBuyFirst = ScorerAIFactory<SplendorGame>("Splendor", "#AI_BuyFirst",
            buyCard, buyReserved, reserve.weight(-1), takeMoneyNeeded.weight(0.1), scorers.simple { 0.0 })
    val aiRandom = ScorerAIFactory<SplendorGame>("Splendor", "#AI_Random", scorers.simple { 0.0 })

    fun ais(): List<ScorerAIFactory<SplendorGame>> = listOf(aiBuyFirst)

}