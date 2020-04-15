package net.zomis.games.dsl

import net.zomis.games.PlayerEliminations
import net.zomis.games.WinResult
import kotlin.math.min

object NimGame {

    data class Nim(val piles: MutableList<Int>, val playerCount: Int, val lastWins: Boolean, val maxPerTurn: Int, var currentPlayer: Int = 0) {
        fun copy(): Nim = Nim(piles.toMutableList(), playerCount, lastWins, maxPerTurn, currentPlayer)

        fun move(move: NimMove, playerEliminations: PlayerEliminations) {
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

    val action = createActionType("Take", NimMove::class)
    val game = createGame<Nim>("Nim") {
        setup(NimConfig::class) {
            players(2..2)
            defaultConfig {
                NimConfig(listOf(21), true, 3)
            }
            init {
                Nim(this.config.piles.toMutableList(), this.playerCount, this.config.lastWins, this.config.maxPerTurn)
            }
        }
        logic {
            action(action) {
                options {
                    optionFrom({ it.piles.indices }) {pileIndex ->
                        optionFrom({ 0..min(it.maxPerTurn, it.piles[pileIndex]) }) {amount ->
                            actionParameter(NimMove(pileIndex, amount))
                        }
                    }
                }
                effect {
                    it.game.move(it.parameter, this.playerEliminations)
                }
                allowed {
                    val pileRemaining = it.game.piles[it.parameter.pileIndex]
                    it.parameter.amount > pileRemaining && it.parameter.amount <= it.game.maxPerTurn
                }
            }
        }
        view {
            value("piles") { it.piles }
            currentPlayer { it.currentPlayer }
            eliminations()
        }
    }

}