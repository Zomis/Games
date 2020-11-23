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
        setup(Config::class) {
            defaultConfig { Config(7) }
            playersFixed(2)
            init { RoyalGameOfUr(config.piecesPerPlayer) }
        }
        gameFlowRules {
            rules.players.singleWinner { game.winner.takeIf { game.isFinished } }
        }
        fun addView(scope: GameFlowStepScope<RoyalGameOfUr>) {
            scope.yieldView("currentPlayer") { game.currentPlayer }
            scope.yieldView("lastRoll") { game.lastRoll }
            scope.yieldView("roll") { game.roll }
            scope.yieldView("pieces") { game.piecesCopy.map { it.toList() }.toList() }
        }
        gameFlow {
            loop {
                step("roll") {
                    require(game.isRollTime())
                    addView(this)
                    yieldAction(game.currentPlayer, roll) {
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
                        addView(this)
                        yieldAction(game.currentPlayer, move) {
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