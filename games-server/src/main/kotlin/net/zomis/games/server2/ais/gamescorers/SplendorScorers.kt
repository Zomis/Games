package net.zomis.games.server2.ais.gamescorers

import net.zomis.games.dsl.GamesImpl
import net.zomis.games.impl.DslSplendor

object SplendorScorers {

    val scorers = GamesImpl.game(DslSplendor.splendorGame).scorers()

    val buyCard = scorers.action(DslSplendor.buy) { 1.0 }
    val buyReserved = scorers.action(DslSplendor.buyReserved) { 1.0 }
    val takeMoneyNeeded = scorers.action(DslSplendor.takeMoney) { action.parameter.moneys.size.toDouble() }
    val reserve = scorers.action(DslSplendor.reserve) { 1.0 }
    val discard = scorers.action(DslSplendor.discardMoney) { 1.0 }

    val aiBuyFirst = scorers.ai("#AI_BuyFirst",
            buyCard, buyReserved, reserve.weight(-1), takeMoneyNeeded.weight(0.1), discard)

    fun ais() = listOf(aiBuyFirst)

}
