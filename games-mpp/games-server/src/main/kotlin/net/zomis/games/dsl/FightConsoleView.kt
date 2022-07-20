package net.zomis.games.dsl

import net.zomis.fights.FightFlow
import net.zomis.fights.displayIntStats
import net.zomis.games.WinResult
import net.zomis.games.impl.DslSplendor

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
    FightFlow(GamesImpl.game(DslSplendor.splendorGame)).fight {
        gameSource {
            fightEvenly(playersCount = 2, gamesPerCombination = 30, ais = gameType.setup().ais())
        }
        val pointsDiff = endGameMetric { game.players.maxOf { it.points } - game.players.minOf { it.points } }
        val points = endGamePlayerMetric {
            game.players[playerIndex].points
        }
        val moneyTaken = actionMetric(DslSplendor.takeMoney) {
            action.parameter.toMoney().moneys
        }
        val cardCosts = actionMetric(DslSplendor.buy) {
            action.parameter.costs
        }
        val moneyPaid = actionMetric(DslSplendor.buy) {
            action.parameter.costs - game.players[action.playerIndex].discounts()
        } // + actionMetric(DslSplendor.buyReserved)...?
        val noblesGotten = endGamePlayerMetric {
            game.players[playerIndex].nobles.size
        }
        val gamesWon = endGamePlayerMetric {
            eliminations.eliminationFor(playerIndex)!!
        }
        grouping {
            // possibly `display` later if more display options are added
            displayIntStats(pointsDiff)
            groupByAndTotal(points) { playerIndex }.displayIntStats()
            groupByAndTotal(points) { ai }.displayIntStats()
            groupByAndTotal(gamesWon) { ai }.displayCount { it.winResult == WinResult.WIN }
//            groupByAndTotal(moneyTaken) { ai }.groupByKeyAndTotal().displayIntStats()

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
    // After displaying results, add a way to search for a specific game/player/action by filtering on metrics.
    //   Such as maximum pointsDiff, then save the replay(s) for those games
}
