package net.zomis.games.server2.ais.gamescorers

import net.zomis.games.impl.*
import net.zomis.games.server2.ais.ScorerAIFactory
import net.zomis.games.server2.ais.scorers.ScorerFactory

object SplendorScorers {

    val scorers = ScorerFactory<SplendorGame>()

    val buyCard = scorers.action(DslSplendor.buy) { 1.0 }
    val buyReserved = scorers.action(DslSplendor.buyReserved) { 1.0 }
    val takeMoneyNeeded = scorers.action(DslSplendor.takeMoney) { action.parameter.moneys.size.toDouble() }
    val reserve = scorers.action(DslSplendor.reserve) { 1.0 }
    val discard = scorers.action(DslSplendor.discardMoney) { 1.0 }

    val aiBuyFirst = ScorerAIFactory<SplendorGame>("Splendor", "#AI_BuyFirst",
            buyCard, buyReserved, reserve.weight(-1), takeMoneyNeeded.weight(0.1), discard)

    fun ais(): List<ScorerAIFactory<SplendorGame>> = listOf(aiBuyFirst)

}
