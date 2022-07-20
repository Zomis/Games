package net.zomis.games.dsl

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.zomis.fights.FightFlow
import net.zomis.games.WinResult
import net.zomis.games.impl.DslSplendor
import net.zomis.games.metrics.displayIntStats
import net.zomis.games.metrics.groupByKeyAndTotal

fun main() {
    /*
    * Fight modes:
    * - Competitive games: fight evenly A/B/C/D/...
    * - All games: X games with just some random AIs from a pool
    * - Cooporative games: X players of AI A/B/C/D/...
    * - Different config
    *
    * Stats:
    * Backgammon/UR: Total steps left for each player over time
    */
    val results = FightFlow(GamesImpl.game(DslSplendor.splendorGame)).fight {
        gameSource {
            fightEvenly(playersCount = 2, gamesPerCombination = 30, ais = gameType.setup().ais())
        }
        val metrics = DslSplendor.Metrics(this)
        grouping {

            // possibly `display` later if more display options are added
            displayIntStats(metrics.pointsDiff, "pointsDiff")
            groupByAndTotal(metrics.points) { playerIndex }.displayIntStats("points per playerIndex")
            groupByAndTotal(metrics.points) { ai }.displayIntStats("points per AI")
            groupByAndTotal(metrics.noblesGotten) { ai }.displayIntStats("nobles per AI")
            groupByAndTotal(metrics.gamesWon) { ai }.displayCount("games won") { it.winResult == WinResult.WIN }
            groupByAndTotalActions(metrics.moneyTaken) { ai }.groupByKeyAndTotal().displayIntStats("moneyTaken")
            groupByAndTotalActions(metrics.cardCosts) { ai }.groupByKeyAndTotal().displayIntStats("cardCosts")
            groupByAndTotalActions(metrics.moneyPaid) { ai }.groupByKeyAndTotal().displayIntStats("moneyPaid")

            // always group Map keys?

            /*
            *                #AI_Hard      #AI_BuyFirst     Total
            * Games Won
            *
            * Points<Int>
            *   Average
            *   Min
            *   Max
            *   Total
            * MoneyTaken<Map<MoneyType, Int>>
            *   As Player0
            *     Red
            *     Green
            *   As Player1
            *     Red
            *     Green
            *   Total
            *     Red
            *       Min/Max/Avg/Total...
            *     Green
            *       Min/Max/Avg/Total...
            *     ...
            *
            * GAMES ONLY:
            *
            * PointsDiff<Int>
            *   Avg/Min/Max/Total
            *
            */



            // pointsDiff.perGame

            // type(MoneyType::class)
        }
    }
    jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(results).also { println(it) }
    // After displaying results, add a way to search for a specific game/player/action by filtering on metrics.
    //   Such as maximum pointsDiff, then save the replay(s) for those games
}
