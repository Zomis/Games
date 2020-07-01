package net.zomis.games.impl

import net.zomis.games.WinResult
import net.zomis.games.common.PlayerIndex
import net.zomis.games.common.next
import net.zomis.games.dsl.ActionRuleScope
import net.zomis.games.dsl.GameCreator
import net.zomis.games.dsl.ReplayableScope
import net.zomis.games.dsl.Viewable
import kotlin.random.Random

data class LiarsDiceBet(val amount: Int, val value: Int): Comparable<LiarsDiceBet> {
    override fun compareTo(other: LiarsDiceBet): Int {
        if (amount != other.amount) return amount - other.amount
        return value - other.value
    }
}

class LiarsDicePlayer(val index: Int, var dice: MutableList<Int>) {
    fun removeDie() {
        if (dice.isEmpty()) return
        dice.removeAt(dice.lastIndex)
    }

    val eliminated: Boolean get() = dice.isEmpty()
}
class LiarsDice(val config: LiarsDiceConfig, val playerCount: Int): Viewable {
    val players = (0 until playerCount).map { LiarsDicePlayer(it, (1..5).toMutableList()) }
    var currentPlayerIndex: Int = 0
    val currentPlayer get() = players[currentPlayerIndex]

    val diceCount get() = players.sumBy { it.dice.size }

    var bet: Pair<LiarsDicePlayer, LiarsDiceBet>? = null
    fun currentBet(): LiarsDiceBet? = bet?.second

    fun isSpotOn() = currentBet() == correctBet(currentBet()!!.value)
    fun isLie() = correctBet(currentBet()!!.value) < currentBet()!!

    fun correctBet(value: Int) = LiarsDiceBet(players.sumBy { player -> player.dice.count { it == value } }, value)
    fun nextPlayer() {
        do {
            this.currentPlayerIndex = this.currentPlayerIndex.next(players.size)
        } while (this.currentPlayer.eliminated)
    }

    override fun toView(viewer: PlayerIndex): Any? {
        return mapOf(
            "currentPlayer" to currentPlayerIndex,
            "config" to config,
            "better" to bet?.first?.index,
            "bet" to bet?.second,
            "players" to players.map {
                mapOf(
                    "playerIndex" to it.index,
                    "dice" to if (it.index == viewer) it.dice else it.dice.map { 0 }
                )
            }
        )
    }

}

data class LiarsDiceConfig(
    val allowHigherQuantityAnyFace: Boolean = true,
    val allowHigherFaceAnyQuantity: Boolean = false,
    val onesAreWild: Boolean = false,
    val allowSpotOn: Boolean = true,
    val spotOnEveryoneLoses: Boolean = false,
    val callingWildsFirstResetsThem: Boolean = false, // point 5 at https://www.wikihow.com/Play-Liar%27s-Dice
    val twoDiceLeftBetOnSum: Boolean = false // If 2 players left with only one die each, bet on sum of the dice. (point 9 in wikihow)
)

object LiarsDiceGame {
    val random = Random.Default
    fun newRound(game: LiarsDice, replayable: ReplayableScope) {
        game.bet = null
        game.players.forEach {
            it.dice = replayable.ints("player-" + it.index) { it.dice.map { random.nextInt(1, 6) } }.toMutableList()
        }
    }

    val factory = GameCreator(LiarsDice::class)
    val liar = factory.singleAction("liar")
    val spotOn = factory.singleAction("spotOn")
    val bet = factory.action("bet", LiarsDiceBet::class)

    val game = factory.game("LiarsDice") {
        setup(LiarsDiceConfig::class) {
            players(2..16)
            defaultConfig { LiarsDiceConfig() }
            init { LiarsDice(config, playerCount) }
        }
        rules {
            gameStart {
                newRound(game, replayable)
            }

            allActions.precondition { game.currentPlayerIndex == playerIndex }

            action(liar) {
                precondition { game.bet != null }
                effect {
                    logRevealAllDice("liar", this)
                    if (game.isLie()) {
                        val losingPlayer = game.bet!!.first
                        log { "$player called liar and is correct! ${player(losingPlayer.index)} loses one die." }
                        losingPlayer.removeDie()
                        game.currentPlayerIndex = losingPlayer.index
                    } else {
                        log { "$player called liar incorrectly and loses one die." }
                        game.currentPlayer.removeDie()
                    }
                    newRound(game, replayable)
                }
            }
            action(spotOn) {
                precondition { game.bet != null }
                precondition { game.config.allowSpotOn }
                effect {
                    logRevealAllDice("spot-on", this)
                    if (game.isSpotOn()) {
                        val losingPlayer = game.bet!!.first
                        if (game.config.spotOnEveryoneLoses) {
                            log { "$player called spot-on and was correct! Everyone loses one die." }
                            game.players.filter { it != game.currentPlayer }.forEach { it.removeDie() }
                        } else {
                            log { "$player called spot-on and was correct! ${player(losingPlayer.index)} loses one die." }
                            losingPlayer.removeDie()
                        }
                        game.currentPlayerIndex = losingPlayer.index
                    } else {
                        log { "$player called spot-on incorrectly and loses one die." }
                        game.currentPlayer.removeDie()
                    }
                    newRound(game, replayable)
                }
            }
            action(bet) {
                choose {
                    val bet = context.game.bet?.second ?: LiarsDiceBet(1, 0)
                    options({ bet.amount..context.game.diceCount }) {amount ->
                        val min = if (amount == bet.amount) bet.value + 1 else 1
                        options({ min..6 }) {value ->
                            parameter(LiarsDiceBet(amount, value))
                        }
                    }
                }
                effect {
                    game.bet = game.currentPlayer to action.parameter
                    game.nextPlayer()
                    log { "$player bets ${action.amount} ${action.value}'s" }
                }
            }
            allActions.after {
                val remaining = eliminations.remainingPlayers()
                game.players.filter { it.eliminated && remaining.contains(it.index) }.forEach {
                    eliminations.result(it.index, WinResult.LOSS)
                }
                if (eliminations.remainingPlayers().size == 1) {
                    eliminations.eliminateRemaining(WinResult.WIN)
                }
                while (game.currentPlayer.eliminated) {
                    game.currentPlayerIndex = game.currentPlayerIndex.next(game.playerCount)
                }
            }
        }
    }

    private fun logRevealAllDice(call: String, scope: ActionRuleScope<LiarsDice, *>) {
        scope.log { "$player calls $call and everyone reveals their dice!" }
        scope.game.players.filter { !it.eliminated }.forEach {
            scope.log { "${player(it.index)} had ${it.dice.sorted().joinToString(", ")}" }
        }
    }

}
