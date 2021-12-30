package net.zomis.games.impl

import net.zomis.games.PlayerEliminationsWrite
import net.zomis.games.WinResult
import net.zomis.games.api.Games
import net.zomis.games.common.Direction8
import net.zomis.games.common.Point
import net.zomis.games.common.PointMove
import net.zomis.games.components.GridPoint
import net.zomis.games.dsl.*
import kotlin.math.abs
import kotlin.math.max

class TTArtax(private val eliminationCallback: PlayerEliminationsWrite,
          playerCount: Int, sizeX: Int, sizeY: Int) {
    val board = Games.components.grid<Int?>(sizeX, sizeY) { _, _ -> null }
    var currentPlayer: Int = 0

    init {
        board.all().forEach { it.value = null }
        board.point(0, 0).value = 0
        board.point(board.sizeX - 1, 0).value = 1

        if (playerCount == 2) {
            board.point(0, board.sizeY - 1).value = 1
            board.point(board.sizeX - 1, board.sizeY - 1).value = 0
        }
        if (playerCount >= 3) {
            board.point(0, board.sizeY - 1).value = 2
        }
        if (playerCount == 4) {
            board.point(board.sizeX - 1, board.sizeY - 1).value = 3
        }
    }

    private fun distance(a: GridPoint<Int?>, b: GridPoint<Int?>): Int {
        return max(abs(a.x - b.x), abs(a.y - b.y))
    }

    fun allowedSource(tile: GridPoint<Int?>): Boolean {
        return tile.valueOrNull() == this.currentPlayer
    }

    fun allowedDestination(source: GridPoint<Int?>, tile: GridPoint<Int?>): Boolean {
        if (tile.valueOrNull() != null) {
            return false
        }

        val tileDistance = distance(tile, source)
        return tileDistance in 1..2
    }

    fun perform(source: GridPoint<Int?>, destination: GridPoint<Int?>): Boolean {
        if (!allowedSource(source) || !allowedDestination(source, destination)) {
            return false
        }
        val tileDistance = distance(destination, source)
        destination.value = this.currentPlayer
        if (tileDistance == 2) {
            source.value = null
        }
        val potentialWinner = this.currentPlayer
        Direction8.values().map { dir ->
            board.point(destination.x + dir.deltaX, destination.y + dir.deltaY)
                .rangeCheck(board)
                ?.takeIf { it.valueOrNull() != null }?.value = potentialWinner
        }

        this.eliminatePlayersWithoutTiles()

        this.currentPlayer = eliminationCallback.nextPlayer(this.currentPlayer)!!

        if (eliminationCallback.remainingPlayers().size == 1) {
            eliminationCallback.eliminateRemaining(WinResult.WIN)
            return true
        }

        val playersThatCanMove = eliminationCallback.remainingPlayers().filter {player ->
            val playerTiles = this.board.all().filter { it.valueOrNull() == player }
            return@filter this.board.all().any { tile -> tile.valueOrNull() == null &&
                playerTiles.any { distance(tile, it) <= 2 } }
        }
        if (playersThatCanMove.size <= 1) {
            // If no moves are possible
            val counts = this.board.all().filter { it.valueOrNull() != null }
                .groupBy { it.value }
                .mapKeys { it.key!! }
                .mapValues { it.value.size }
                .entries.sortedByDescending { it.value }
                .map { it.key to it.value }

            eliminationCallback.eliminateBy(counts, compareBy { it })
        }
        while (!playersThatCanMove.contains(this.currentPlayer)) {
            this.currentPlayer = eliminationCallback.nextPlayer(this.currentPlayer) ?: return true
        }

        return true
    }

    private fun eliminatePlayersWithoutTiles() {
        val losingPlayers = eliminationCallback.remainingPlayers().filter { player ->
            this.board.all().none { it.valueOrNull() == player }
        }
        eliminationCallback.eliminateMany(losingPlayers, WinResult.LOSS)
    }

}

object ArtaxGame {

    val factory = GameCreator(TTArtax::class)
    val moveAction = factory.action("move", PointMove::class)
    val gameArtax = factory.game("Artax") {
        val sizeX = config("x") { 7 }
        val sizeY = config("y") { 7 }
        setup {
            players(2..4)
            init {
                TTArtax(eliminationCallback, playerCount, config(sizeX), config(sizeY))
            }
        }
        actionRules {
            action(moveAction) {
                precondition { game.currentPlayer == playerIndex }
                choose {
                    options({ game.board.points().filter { game.allowedSource(game.board.point(it)) } }) {source ->
                        options({ game.board.points().filter { game.allowedDestination(game.board.point(source), game.board.point(it)) } }) {destination ->
                            parameter(PointMove(Point(source.x, source.y), Point(destination.x, destination.y)))
                        }
                    }
                }
                requires { game.allowedSource(game.board.point(action.parameter.source)) }
                requires {
                    game.allowedDestination(game.board.point(action.parameter.source), game.board.point(action.parameter.destination))
                }
                effect {
                    game.perform(game.board.point(action.parameter.source), game.board.point(action.parameter.destination))
                }
            }
            view("actionName") { moveAction.name }
            view("currentPlayer") { game.currentPlayer }
            view("board") { game.board.view { mapOf("owner" to it) } }
        }
    }

}
