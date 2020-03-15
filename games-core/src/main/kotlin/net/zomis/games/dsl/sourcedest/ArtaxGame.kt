package net.zomis.games.dsl.sourcedest

import net.zomis.games.Map2DPoint
import net.zomis.games.Map2DX
import net.zomis.games.PlayerEliminationCallback
import net.zomis.games.WinResult
import net.zomis.games.dsl.*
import net.zomis.tttultimate.Direction8
import kotlin.math.abs
import kotlin.math.max

fun Int.next(playerCount: Int): Int = (this + 1) % playerCount
class TTArtax(private val eliminationCallback: PlayerEliminationCallback,
          playerCount: Int, sizeX: Int, sizeY: Int) {
    val board = Map2DX<Int?>(sizeX, sizeY) { _, _ -> null }
    var currentPlayer: Int = 0

    init {
        board.all().forEach { it.value = null }
        board.point(0, 0).value = 0
        board.point(0, board.sizeY - 1).value = 1

        if (playerCount == 2) {
            board.point(board.sizeX - 1, 0).value = 1
            board.point(board.sizeX - 1, board.sizeY - 1).value = 0
        }
        if (playerCount >= 3) {
            board.point(board.sizeX - 1, 0).value = 2
        }
        if (playerCount == 4) {
            board.point(board.sizeX - 1, board.sizeY - 1).value = 3
        }
    }

    private fun distance(a: Map2DPoint<Int?>, b: Map2DPoint<Int?>): Int {
        return max(abs(a.x - b.x), abs(a.y - b.y))
    }

    fun allowedSource(tile: Map2DPoint<Int?>): Boolean {
        return tile.value == this.currentPlayer
    }

    fun allowedDestination(source: Map2DPoint<Int?>, tile: Map2DPoint<Int?>): Boolean {
        if (tile.value != null) {
            return false
        }

        val tileDistance = distance(tile, source)
        return tileDistance in 1..2
    }

    fun perform(source: Map2DPoint<Int?>, destination: Map2DPoint<Int?>): Boolean {
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
                ?.takeIf { it.value != null }?.value = potentialWinner
        }

        this.eliminatePlayersWithoutTiles()

        this.currentPlayer = eliminationCallback.nextPlayer(this.currentPlayer)!!

        if (eliminationCallback.remainingPlayers().size == 1) {
            eliminationCallback.eliminateRemaining(WinResult.WIN)
            return true
        }

        val playersThatCanMove = eliminationCallback.remainingPlayers().filter {player ->
            val playerTiles = this.board.all().filter { it.value == player }
            return@filter this.board.all().any { tile -> tile.value == null &&
                playerTiles.any { distance(tile, it) <= 2 } }
        }
        if (playersThatCanMove.isEmpty()) {
            // If no moves are possible
            val counts = this.board.all().filter { it.value != null }
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
            this.board.all().none { it.value == player }
        }
        eliminationCallback.eliminateMany(losingPlayers, WinResult.LOSS)
    }

}

private val moveAction = createActionType("move", PointMove::class)
object ArtaxGame {

    val gameArtax = createGame<TTArtax>("Artax") {
        val grid = gridSpec<Int?> {
            size(model.board.sizeX, model.board.sizeY)
            getter(model.board::get)
        }
        setup(Point::class) {
            players(2..4)
            defaultConfig { Point(7, 7) }
            init { TTArtax(eliminationCallback, playerCount, config.x, config.y) }
        }
        logic {
            action(moveAction) {
                options {
                    optionFrom({ game -> game.board.points().filter { game.allowedSource(game.board.point(it)) } }) {source ->
                        optionFrom({ game -> game.board.points().filter { game.allowedDestination(game.board.point(source), game.board.point(it)) } }) {destination ->
                            actionParameter(PointMove(Point(source.x, source.y), Point(destination.x, destination.y)))
                        }
                    }
                }
                allowed {
                    it.game.currentPlayer == it.playerIndex &&
                        it.game.allowedSource(it.game.board.point(it.parameter.source)) &&
                        it.game.allowedDestination(it.game.board.point(it.parameter.source),
                            it.game.board.point(it.parameter.destination))
                }
                effect {
                    it.game.perform(it.game.board.point(it.parameter.source),
                        it.game.board.point(it.parameter.destination))
                }
            }
        }
        view {
            eliminations()
            currentPlayer { it.currentPlayer }
            grid("board", grid) {
                owner { it }
            }
        }
    }

}
