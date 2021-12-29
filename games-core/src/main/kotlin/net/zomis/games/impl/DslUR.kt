package net.zomis.games.impl

import net.zomis.games.dsl.GameCreator
import net.zomis.games.dsl.flow.GameFlowStepScope
import net.zomis.games.ur.RoyalGameOfUr

object DslUR {

    data class Config(val piecesPerPlayer: Int)
    val factory = GameCreator(RoyalGameOfUr::class)
    val roll = factory.action("roll", Unit::class)
    val move = factory.action("move", Int::class)
    val gameUR = factory.game("DSL-UR") {
        val piecesPerPlayer = config("piecesPerPlayer") { 7 }
        setup {
            playersFixed(2)
            init { RoyalGameOfUr(config(piecesPerPlayer)) }
        }
        gameFlowRules {
            rules.players.singleWinner { game.winner.takeIf { game.isFinished } }
            beforeReturnRule("view") {
                view("actions") {
                    mapOf("roll" to action(roll).anyAvailable()) + action(move).options().associateBy { "move-$it" }
                }
                view("currentPlayer") { game.currentPlayer }
                view("lastRoll") { game.lastRoll }
                view("roll") { game.roll }
                view("pieces") { game.piecesCopy.map { it.toList() }.toList() }
            }
        }
        gameFlow {
            loop {
                step("roll") {
                    require(game.isRollTime())
                    yieldAction(roll) {
                        precondition { playerIndex == game.currentPlayer }
                        perform {
                            val roll = replayable.int("roll") { game.randomRoll() }
                            if (game.canMove(roll)) {
                                log { "$player rolled $roll" }
                            } else {
                                log { "$player rolled $roll and cannot move" }
                            }
                            game.doRoll(roll)
                        }
                    }
                }
                if (game.isMoveTime) {
                    step("move") {
                        yieldAction(move) {
                            precondition { playerIndex == game.currentPlayer }
                            options { 0 until RoyalGameOfUr.EXIT }
                            requires { game.canMove(game.currentPlayer, action.parameter, game.roll) }
                            perform { game.move(game.currentPlayer, action.parameter, game.roll) }
                        }
                    }
                }
            }
        }
    }

}