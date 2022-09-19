package net.zomis.games.impl.paths

import net.zomis.games.api.GamesApi
import net.zomis.games.components.Path
import net.zomis.games.components.Paths
import net.zomis.games.common.next
import net.zomis.games.common.toSingleList
import net.zomis.games.dsl.Actionable
import kotlin.random.Random

object Backgammon {

    data class PiecePosition(var playerIndex: Int, var count: Int) {
        fun set(playerIndex: Int, count: Int) {
            this.playerIndex = playerIndex
            this.count = count
        }
    }
    data class PieceMove(val piece: Int, val steps: Int)
    class Model {
        var discardedDice: List<Int> = emptyList()
        var currentPlayerIndex: Int = 0
        val points = (1..24).map { PiecePosition(0, 0) }
        val bars = (0..1).map { PiecePosition(it, 0) }
        val out = (0..1).map { PiecePosition(it, 0) }
        val dice = mutableListOf<Int>()
        val paths = (0..1).map { Paths.from(bars[it]).through(if (it == 0) points else points.reversed()).then(out[it]).build() }

        fun canMove(piece: Int, roll: Int): Boolean {
            // TODO: "if either number can be played but not both, the player must play the larger one"
            val path = paths[currentPlayerIndex]
            val source = path.pos[piece]
            if (source.playerIndex != currentPlayerIndex || source.count == 0) {
                return false
            }
            if (path.pos[0].count > 0 && piece != 0) {
                // Must move out first
                return false
            }
            if (piece + roll > 24) {
                return allowBearOff(piece, roll)
            }
            val destination = path.pos.getOrNull(piece + roll) ?: return false
            return destination.playerIndex == currentPlayerIndex || destination.count <= 1
        }

        fun move(piece: Int, roll: Int) {
            val path = paths[currentPlayerIndex]
            val source = path.pos[piece]
            val targetPos = piece + roll
            val destination = path.pos[targetPos.coerceAtMost(path.pos.lastIndex)]
            source.count--
            if (destination.count == 1 && destination.playerIndex != currentPlayerIndex) {
                destination.count = 0
                paths[1 - currentPlayerIndex].pos[0].count++
            }
            destination.playerIndex = currentPlayerIndex
            destination.count++
        }

        fun playerPieces(): List<Int> {
            return paths[currentPlayerIndex].pos.withIndex().filter {
                it.value.playerIndex == currentPlayerIndex && it.value.count > 0
            }.map { it.index }
        }

        fun allowBearOff(piece: Int, steps: Int): Boolean {
            val playerPieces = this.playerPieces()
            // Allow bear off if everything is over 18 and roll is exact, or if this piece is closest to goal
            val allHome = playerPieces.all { it > 18 } && piece + steps == 25
            return allHome || playerPieces.all { it >= piece }
        }

        fun diceLocked(steps: Int): Boolean {
            val playerPieces = this.playerPieces()
            return playerPieces.none { canMove(it, steps) }
        }
    }

    val random = Random.Default
    val factory = GamesApi.gameCreator(Model::class)
    val roll = factory.action("roll", Unit::class)
    val move = factory.action("move", PieceMove::class)
    val game = factory.game("Backgammon") {
        setup {
            playersFixed(2)
            init { Model() }
        }
        gameFlow {
            step("setup") {
                (0..1).forEach { playerIndex ->
                    game.paths[playerIndex].pos[1].set(playerIndex, 2)
                    game.paths[playerIndex].pos[12].set(playerIndex, 5)
                    game.paths[playerIndex].pos[17].set(playerIndex, 3)
                    game.paths[playerIndex].pos[19].set(playerIndex, 5)
                }
            }
            step("roll who starts") {
                if (game.dice.distinct().size <= 1) {
                    game.dice.clear()
                    game.dice.addAll(listOf(0, 0))
                }
                yieldAction(roll) {
                    precondition { game.dice[playerIndex] == 0 }
                    perform { game.dice[playerIndex] = replayable.int("roll") { random.nextInt(6) + 1 } }
                }
            }.loopUntil { game.dice.filter { it > 0 }.distinct().size == 2 }
            game.currentPlayerIndex = game.dice.indexOf(game.dice.maxOrNull()!!)

            loop {
                step("move") {
                    // Remove invalid dice
                    if (game.dice.all { game.diceLocked(it) }) {
                        game.discardedDice = game.dice.toList()
                        game.dice.clear()
                    }
                    yieldAction(move) {
                        precondition { playerIndex == game.currentPlayerIndex }
                        choose {
                            options({ game.dice.distinct() }) { steps ->
                                options({ game.playerPieces() }) { piece ->
                                    parameter(PieceMove(piece, steps))
                                }
                            }
                        }
                        requires { game.canMove(action.parameter.piece, action.parameter.steps) }
                        perform {
                            game.move(action.parameter.piece, action.parameter.steps)
                            game.dice.remove(action.parameter.steps)
                            if (game.playerPieces().all { it > 24 }) {
                                eliminations.singleWinner(game.currentPlayerIndex)
                            }
                        }
                    }
                }.loopUntil { game.dice.isEmpty() }

                game.currentPlayerIndex = game.currentPlayerIndex.next(2)

                step("roll") {
                    yieldAction(roll) {
                        precondition { playerIndex == game.currentPlayerIndex }
                        options { Unit.toSingleList() }
                        perform {
                            game.dice.clear()
                            game.discardedDice = emptyList()
                            game.dice.add(replayable.int("roll1") { random.nextInt(6) + 1 })
                            game.dice.add(replayable.int("roll2") { random.nextInt(6) + 1 })
                            if (game.dice.distinct().size == 1) {
                                game.dice.addAll(game.dice)
                            }
                        }
                    }
                }
            }
        }
        gameFlowRules {
            beforeReturnRule("view") {
                view("currentPlayer") { game.currentPlayerIndex }
                view("dice") { game.dice }
                view("viewer") { viewer ?: 0 }
                view("board") {
                    game.paths[0].pos.drop(1).take(24).map {
                        mapOf("playerIndex" to it.playerIndex, "count" to it.count)
                    }
                }
                view("out") {
                    (0..1).map { game.paths[it].pos[25] }
                }
                view("middle") {
                    (0..1).map { game.paths[it].pos[0] }
                }
                view("discardedDice") { game.discardedDice }
                view("actions") {
                    val noChoices = actionsChosen().chosen()?.chosen?.isEmpty() ?: true
                    mapOf(
                        "roll" to action(roll).anyAvailable(),
                        "dice" to if (noChoices && action(move).anyAvailable()) game.dice.toList().distinct() else emptyList(),
                        "piece" to if (actionsChosen().chosen()?.chosen?.size == 1) action(move).nextSteps(Int::class) else emptyList()
                    )
                }
                view("path") {
                    val playerIndex: Int = viewer ?: 0
                    game.paths[playerIndex].pos.map {
                        mapOf("playerIndex" to it.playerIndex, "count" to it.count)
                    }
                }
            }
        }
        val roll = scorers.isAction(roll)
        val moveFromSafe = scorers.actionConditional(move) {
            pieceInfo(action).source.count >= 3
        }
        val moveToProtect = scorers.actionConditional(move) {
            pieceInfo(action).destination.let { it.count == 1 && it.playerIndex == action.playerIndex }
        }
        val knockout = scorers.actionConditional(move) {
            pieceInfo(action).destination.let { it.count == 1 && it.playerIndex != action.playerIndex }
        }
        val moveFromBack = scorers.action(move) { 1 - action.parameter.piece.toDouble() / 30 }
        scorers.ai("#AI_Simple", roll, moveFromBack, moveFromSafe, moveToProtect)
        scorers.ai("#AI_Knockout", roll, moveFromBack, moveFromSafe, moveToProtect, knockout)
    }
    data class PieceInfo(val path: Path<PiecePosition>, val source: PiecePosition, val destination: PiecePosition)
    fun pieceInfo(action: Actionable<Model, PieceMove>): PieceInfo {
        val path = action.game.paths[action.playerIndex]
        val destination = action.parameter.piece + action.parameter.steps
        return PieceInfo(path, path.pos[action.parameter.piece], path.pos[destination.coerceAtMost(path.pos.size - 1)])
    }

}