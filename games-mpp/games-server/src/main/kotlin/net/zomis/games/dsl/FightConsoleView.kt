package net.zomis.games.dsl

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.zomis.fights.FightFlow
import net.zomis.fights.FightScope
import net.zomis.games.WinResult
import net.zomis.games.impl.DslSplendor
import net.zomis.games.impl.HanabiGame
import net.zomis.games.listeners.FileReplayOnEnd
import net.zomis.games.listeners.ReplayListener
import net.zomis.games.metrics.displayIntStats
import net.zomis.games.metrics.groupByKeyAndTotal
import java.time.Instant
import kotlin.io.path.Path
import kotlin.io.path.createDirectories

val fightSaves = Path("db/fights").also { it.createDirectories() }

class FightConsoleView<T: Any>(val entryPoint: GameEntryPoint<T>) {

    fun fight(block: FightScope<T>.() -> Unit) = FightFlow(entryPoint).fight(block)

}

fun splendorFight(): Map<String, Any> {
    return FightConsoleView(GamesImpl.game(DslSplendor.splendorGame)).fight {
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
            groupByAndTotalActions(metrics.discarded) { ai }.groupByKeyAndTotal().displayIntStats("discarded")

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
}

fun hanabiFight(saveReplays: Boolean): Map<String, Any> {
    return FightConsoleView(GamesImpl.game(HanabiGame.game)).fight {
        gameSource {
            collaborative(playersCount = 3, gamesPerCombination = 30, ais = this.gameType.setup().ais())
        }
        if (saveReplays) {
            extraGameListeners {
                val replay = listener { ReplayListener(HanabiGame.game.name) }
                listener {
                    val timeStamp = Instant.now().epochSecond.toString()
                    val fileName = "$timeStamp -- ${fightSetup.players.first().name} - ${fightSetup.iteration}.json"
                    FileReplayOnEnd(fightSaves.resolve(fileName), replay)
                }
            }
        }
        val metrics = HanabiGame.Metrics(this)
        this.grouping {
            groupBy(metrics.fails) { fightSetup.players.first().name }.displayIntStats("fails")
            groupByAndTotalActions(metrics.discarded) { ai.name }.displayIntStats("discard count")
            groupByAndTotalActions(metrics.cluesUsed) { ai.name }.displayIntStats("cards given clues about")
            groupBy(metrics.points) { fightSetup.players.first().name }.displayIntStats("points")
        }
    }
}

fun main() {
    /*
    * Fight modes:
    * x Competitive games: fight evenly A/B/C/D/...
    * x Cooporative games: X players of AI A/B/C/D/...
    * - All games: X games with just some random AIs from a pool
    * - Different config (or other things?)
    *
    * Stats:
    * Backgammon/UR: Total steps left for each player over time
    */
    val results = hanabiFight(saveReplays = false)
    jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(results).also { println(it) }
    // After displaying results, add a way to search for a specific game/player/action by filtering on metrics.
    //   Such as maximum pointsDiff, then save the replay(s) for those games
}
