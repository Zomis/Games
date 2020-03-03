package net.zomis.games.dsl.sourcedest

import net.zomis.games.dsl.*
import net.zomis.tttultimate.Direction8
import net.zomis.tttultimate.TTBase
import net.zomis.tttultimate.TTFactories
import net.zomis.tttultimate.TTPlayer
import kotlin.math.abs
import kotlin.math.max

abstract class TTControllerSourceDestination(val board: TTBase) {
    var currentPlayer: TTPlayer = TTPlayer.X

    fun reset() {
        this.currentPlayer = TTPlayer.X
        this.onReset()
    }
    open fun onReset() {}

    abstract fun allowedSource(tile: TTBase): Boolean
    abstract fun allowedDestination(source: TTBase, tile: TTBase): Boolean
    abstract fun perform(source: TTBase, destination: TTBase): Boolean
}

class TTArtax(game: TTBase): TTControllerSourceDestination(game) {
    init {
        this.onReset()
    }

    private fun distance(a: TTBase, b: TTBase): Int {
        return max(abs(a.x - b.x), abs(a.y - b.y))
    }

    override fun allowedSource(tile: TTBase): Boolean {
        return tile.wonBy == this.currentPlayer
    }

    override fun allowedDestination(source: TTBase, tile: TTBase): Boolean {
        if (tile.isWon) {
            return false
        }

        val tileDistance = distance(tile, source)
        return tileDistance in 1..2
    }

    override fun onReset() {
        board.subs().forEach { it.setPlayedBy(TTPlayer.NONE) }
        board.getSub(0, 0)!!.setPlayedBy(TTPlayer.X)
        board.getSub(board.sizeX - 1, board.sizeY - 1)!!.setPlayedBy(TTPlayer.X)

        board.getSub(0, board.sizeY - 1)!!.setPlayedBy(TTPlayer.O)
        board.getSub(board.sizeX - 1, 0)!!.setPlayedBy(TTPlayer.O)
    }

    override fun perform(source: TTBase, destination: TTBase): Boolean {
        if (!allowedSource(source) || !allowedDestination(source, destination)) {
            return false
        }
        val tileDistance = distance(destination, source)
        destination.setPlayedBy(this.currentPlayer)
        if (tileDistance == 2) {
            source.setPlayedBy(TTPlayer.NONE)
        }
        val potentialWinner = this.currentPlayer
        Direction8.values().map { dir ->
            destination.parent!!.getSub(destination.x + dir.deltaX, destination.y + dir.deltaY)?.takeIf { it.isWon }?.setPlayedBy(potentialWinner)
        }
        this.currentPlayer = this.currentPlayer.next()

        val currentPlayerTiles = this.board.subs().filter { it.wonBy == this.currentPlayer }
        if (currentPlayerTiles.isEmpty()) {
            this.board.setPlayedBy(potentialWinner)
        }
        if (this.board.subs().none { tile -> !tile.isWon && currentPlayerTiles.any { distance(tile, it) <= 2 } }) {
            val current = currentPlayerTiles.size
            val opponent = this.currentPlayer.next().let { opp -> this.board.subs().filter { it.wonBy == opp } }.size
            this.board.setPlayedBy(when {
                current > opponent -> this.currentPlayer
                opponent > current -> this.currentPlayer.next()
                else -> TTPlayer.XO
            })
        }
        return true
    }

}

class TTQuixoController(game: TTBase): TTControllerSourceDestination(game) {
    private fun isBorder(tile: TTBase): Boolean {
        val parent = tile.parent ?: return false
        return tile.x == 0 || tile.y == 0 || tile.x == parent.sizeX - 1 || tile.y == parent.sizeY - 1
    }

    private fun direction(source: TTBase, tile: TTBase): Direction8 {
        return when {
            tile.x > source.x -> Direction8.W
            tile.x < source.x -> Direction8.E
            tile.y > source.y -> Direction8.N
            tile.y < source.y -> Direction8.S
            else -> throw IllegalStateException("Dir is null for source $source and tile $tile")
        }
    }

    override fun allowedSource(tile: TTBase): Boolean {
        val allowedOwner = !tile.isWon || tile.wonBy == this.currentPlayer
        return isBorder(tile) && allowedOwner
    }

    override fun allowedDestination(source: TTBase, tile: TTBase): Boolean {
        if (tile == source || !isBorder(tile)) {
            return false
        }

        val dir = direction(source, tile)
        val parent = tile.parent!!
        val otherSide = parent.getSub(tile.x - dir.deltaX, tile.y - dir.deltaY)
        if (otherSide != null) {
            return false
        }

        var curr: TTBase? = tile
        while (curr != null) {
            curr = parent.getSub(curr.x + dir.deltaX, curr.y + dir.deltaY)
            if (curr == source) {
                return true
            }
        }
        return false
    }

    override fun onReset() {
        this.board.subs().forEach { it.setPlayedBy(TTPlayer.NONE) }
    }

    override fun perform(source: TTBase, destination: TTBase): Boolean {
        source.setPlayedBy(TTPlayer.NONE)
        val target = source
        val dir = direction(source, destination)
        var sub: TTBase? = target
        while (sub != destination && sub != null) {
            val parent = sub.parent!!
            val next = parent.getSub(sub.x + dir.deltaX*-1, sub.y + dir.deltaY*-1)
            if (next != null) {
                sub.setPlayedBy(next.wonBy)
            }
            sub = next
        }
        destination.setPlayedBy(this.currentPlayer)
        this.currentPlayer = this.currentPlayer.next()
        this.board.determineWinner()
        return true
    }

}

class TTSourceDestinationGames {

    val moveAction = createActionType("move", PointMove::class)
    val gameArtax = createGame<TTControllerSourceDestination>("Artax") {
        val grid = gridSpec<TTBase> {
            size(model.board.sizeX, model.board.sizeY)
            getter { x, y -> model.board.getSmallestTile(x, y)!! }
        }
        setup(Point::class) {
            defaultConfig { Point(7, 7) }
            init { conf -> TTArtax(TTFactories().classicMNK(conf!!.x, conf.y, 0)) }
        }
        logic(ttLogic())
        view(ttView(grid))
    }

    val gameQuixo = createGame<TTControllerSourceDestination>("Quixo") {
        val grid = gridSpec<TTBase> {
            size(model.board.sizeX, model.board.sizeY)
            getter { x, y -> model.board.getSub(x, y)!! }
        }
        setup(TTOptions::class) {
            defaultConfig { TTOptions(5, 5, 5) }
            init { conf -> TTQuixoController(TTFactories().classicMNK(conf!!.m, conf.n, conf.k)) }
        }
        logic(ttLogic())
        view(ttView(grid))
    }

    private val winner: (TTBase) -> Int? = {
        when {
            it.isWon -> it.wonBy.index() // returns -1 for both X and O
            else -> null
        }
    }

    private fun ttView(grid: GridDsl<TTControllerSourceDestination, TTBase>): GameViewDsl<TTControllerSourceDestination> = {
        currentPlayer { it.currentPlayer.index() }
        winner { winner(it.board) }
        grid("board", grid) {
            owner { it.wonBy.index().takeIf {n -> n >= 0 } }
        }
    }

    private fun TTControllerSourceDestination.point(point: Point): TTBase {
        return this.board.getSub(point.x, point.y)!!
    }
    private fun TTControllerSourceDestination.grid(): List<Point> {
        return this.board.subs().map { Point(it.x, it.y) }
    }

    private fun ttLogic(): GameLogicDsl<TTControllerSourceDestination> = {
        winner { winner(it.board) }
        action(moveAction) {
            options {
                optionFrom({ game -> game.grid().filter { game.allowedSource(game.point(it)) }.toTypedArray() }) {source ->
                    optionFrom({ game -> game.grid().filter { game.allowedDestination(game.point(source), game.point(it)) }.toTypedArray() }) {destination ->
                        actionParameter(PointMove(Point(source.x, source.y), Point(destination.x, destination.y)))
                    }
                }
            }
            allowed {
                it.game.currentPlayer.index() == it.playerIndex &&
                  it.game.allowedSource(it.game.point(it.parameter.source)) &&
                    it.game.allowedDestination(it.game.point(it.parameter.source), it.game.point(it.parameter.destination))
            }
            effect {
                it.game.perform(it.game.point(it.parameter.source), it.game.point(it.parameter.destination))
            }
        }
    }

}