package net.zomis.games.dsl

import net.zomis.games.PlayerEliminationsWrite
import net.zomis.games.WinResult
import kotlin.math.min

object NimGame {

    data class Nim(val piles: MutableList<Int>, val playerCount: Int, val lastWins: Boolean, val maxPerTurn: Int, var currentPlayer: Int = 0) {
        fun copy(): Nim = Nim(piles.toMutableList(), playerCount, lastWins, maxPerTurn, currentPlayer)

        fun move(move: NimMove, playerEliminations: PlayerEliminationsWrite) {
            piles[move.pileIndex] -= move.amount
            if (piles.all { it == 0 }) {
                playerEliminations.result(currentPlayer, if (lastWins) WinResult.WIN else WinResult.LOSS)
                return
            }
            currentPlayer = (currentPlayer + 1) % playerCount
        }
    }
    data class NimConfig(val piles: List<Int>, val lastWins: Boolean, val maxPerTurn: Int)
    data class NimMove(val pileIndex: Int, val amount: Int)

    val factory = GameCreator(Nim::class)
    val nimAction = factory.action("Take", NimMove::class)
    val game = factory.game("Nim") {
        val nimConfig = config("nim") { NimConfig(listOf(21), true, 3) }
        setup {
            playersFixed(2)
            init {
                val config = config(nimConfig)
                Nim(config.piles.toMutableList(), this.playerCount, config.lastWins, config.maxPerTurn)
            }
        }
        actionRules {
            action(nimAction) {
                choose {
                    options({ game.piles.indices }) {pileIndex ->
                        options({ 0..min(game.maxPerTurn, game.piles[pileIndex]) }) {amount ->
                            parameter(NimMove(pileIndex, amount))
                        }
                    }
                }
                requires {
                    val pileRemaining = game.piles[action.parameter.pileIndex]
                    action.parameter.amount > pileRemaining && action.parameter.amount <= game.maxPerTurn
                }
                effect {
                    game.move(action.parameter, eliminations)
                }
            }
            view("piles") { game.piles }
            view("currentPlayer") { game.currentPlayer }
        }
    }

}