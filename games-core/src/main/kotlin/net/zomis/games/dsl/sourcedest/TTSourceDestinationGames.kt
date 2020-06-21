package net.zomis.games.dsl.sourcedest

import net.zomis.games.WinResult
import net.zomis.games.common.Point
import net.zomis.games.common.PointMove
import net.zomis.games.dsl.*
import net.zomis.tttultimate.Direction8
import net.zomis.tttultimate.TTBase
import net.zomis.tttultimate.TTFactories
import net.zomis.tttultimate.TTPlayer

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

    val gameQuixo = createGame<TTControllerSourceDestination>("Quixo") {
        val grid = gridSpec<TTBase> {
            size(model.board.sizeX, model.board.sizeY)
            getter { x, y -> model.board.getSub(x, y)!! }
        }
        setup(TTOptions::class) {
            defaultConfig { TTOptions(5, 5, 5) }
            init { conf -> TTQuixoController(TTFactories().classicMNK(conf!!.m, conf.n, conf.k)) }
        }
        rules(ttRules())
        view(ttView(grid))
    }

    private fun ttRules(): GameRules<TTControllerSourceDestination>.() -> Unit = {
        allActions.precondition { playerIndex == game.currentPlayer.index() }
        action(moveAction) {
            choose {
                options({ game.grid().filter { game.allowedSource(game.point(it)) } }) {source ->
                    options({ game.grid().filter { game.allowedDestination(game.point(source), game.point(it)) } }) {destination ->
                        parameter(PointMove(Point(source.x, source.y), Point(destination.x, destination.y)))
                    }
                }
            }
            requires {
                game.allowedSource(game.point(action.parameter.source)) &&
                        game.allowedDestination(game.point(action.parameter.source), game.point(action.parameter.destination))
            }
            effect {
                game.perform(game.point(action.parameter.source), game.point(action.parameter.destination))
            }
        }
        allActions.after {
            if (game.board.isWon) {
                if (game.board.wonBy.index() < 0) eliminations.eliminateRemaining(WinResult.DRAW)
                else {
                    eliminations.result(game.board.wonBy.index(), WinResult.WIN)
                    eliminations.eliminateRemaining(WinResult.LOSS)
                }
            }
        }
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

}