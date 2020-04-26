package net.zomis.games.dsl

import net.zomis.games.ur.RoyalGameOfUr

class DslUR {

    val roll = createActionType("roll", Unit::class)
    val move = createActionType("move", Int::class)
    val gameUR = createGame<RoyalGameOfUr>("UR") {
        setup(Unit::class) {
            playersFixed(2)
            init { RoyalGameOfUr() }
        }
        logic {
            winner { game -> game.winner.takeIf { game.isFinished } }
            simpleAction(roll) {
                allowed { it.game.currentPlayer == it.playerIndex && it.game.isRollTime() }
                effect {
                    val rollResult = it.game.doRoll()
                    state("roll", rollResult)
                }
                replayEffect {
                    it.game.doRoll(state("roll") as Int)
                }
            }
            intAction(move, {0 until RoyalGameOfUr.EXIT}) {
                allowed { it.game.currentPlayer == it.playerIndex &&
                    it.game.isMoveTime && it.game.canMove(it.game.currentPlayer, it.parameter, it.game.roll) }
                effect {
                    it.game.move(it.game.currentPlayer, it.parameter, it.game.roll)
                }
            }
        }
        view {
            currentPlayer { it.currentPlayer }
            winner { game -> game.winner.takeIf { game.isFinished } }
            state("lastRoll") { this.fullState("roll") ?: 0 }
            value("roll") { it.roll }
            value("pieces") { game -> game.piecesCopy.map { it.toList() }.toList() }
        }
    }

}