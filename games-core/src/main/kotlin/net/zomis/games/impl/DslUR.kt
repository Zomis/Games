package net.zomis.games.impl

import net.zomis.games.dsl.GameCreator
import net.zomis.games.ur.RoyalGameOfUr

object DslUR {

    val factory = GameCreator(RoyalGameOfUr::class)
    val roll = factory.action("roll", Unit::class)
    val move = factory.action("move", Int::class)
    val gameUR = factory.game("DSL-UR") {
        setup(Unit::class) {
            playersFixed(2)
            init { RoyalGameOfUr() }
        }
        rules {
            allActions.precondition { game.currentPlayer == playerIndex }
            action(roll) {
                precondition { game.isRollTime() }
                effect {
                    val roll = replayable.int("roll") { game.randomRoll() }
                    if (game.canMove(roll)) {
                        log { "$player rolled $roll" }
                    } else {
                        log { "$player rolled $roll and cannot move" }
                    }
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
            value("lastRoll") { it.lastRoll }
            value("roll") { it.roll }
            value("pieces") { game -> game.piecesCopy.map { it.toList() }.toList() }
        }
    }

}