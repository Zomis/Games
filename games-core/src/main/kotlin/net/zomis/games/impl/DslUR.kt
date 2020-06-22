package net.zomis.games.impl

import net.zomis.games.dsl.GameCreator
import net.zomis.games.dsl.createGame
import net.zomis.games.ur.RoyalGameOfUr

object DslUR {

    val factory = GameCreator(RoyalGameOfUr::class)
    val roll = factory.action("roll", Unit::class)
    val move = factory.action("move", Int::class)
    val gameUR = createGame<RoyalGameOfUr>("UR") {
        setup(Unit::class) {
            playersFixed(2)
            init { RoyalGameOfUr() }
        }
        rules {
            allActions.precondition { game.currentPlayer == playerIndex }
            action(roll) {
                precondition { game.isRollTime() }
                effect {
                    val roll = replayable.int("roll") { game.doRoll() }
                    game.doRoll(roll)
                }
            }
            action(move) {
                options { 0 until RoyalGameOfUr.EXIT }
                precondition { game.isMoveTime }
                requires { game.canMove(game.currentPlayer, action.parameter, game.roll) }
                effect { game.move(game.currentPlayer, action.parameter, game.roll) }
            }
            allActions.after {
                game.winner.takeIf { game.isFinished }?.let { eliminations.singleWinner(it) }
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