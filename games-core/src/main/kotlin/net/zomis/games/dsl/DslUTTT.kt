package net.zomis.games.dsl

import net.zomis.tttultimate.Direction8
import net.zomis.tttultimate.TTBase
import net.zomis.tttultimate.TTFactories
import net.zomis.tttultimate.TTPlayer
import net.zomis.tttultimate.games.*

data class TTOptions(val m: Int, val n: Int, val k: Int)
fun TTPlayer.index(): Int {
    return when (this) {
        TTPlayer.X -> 0
        TTPlayer.O -> 1
        TTPlayer.NONE -> -1
        TTPlayer.XO -> -1
        TTPlayer.BLOCKED -> -1
    }
}

class TTQuixoController(game: TTBase): TTController(game) {
    private var emptyTile: TTBase? = null

    private fun isBorder(tile: TTBase): Boolean {
        val parent = tile.parent ?: return false
        return tile.x == 0 || tile.y == 0 || tile.x == parent.sizeX - 1 || tile.y == parent.sizeY - 1
    }

    private fun direction(tile: TTBase): Direction8 {
        val target = emptyTile!!
        return when {
            tile.x > target.x -> Direction8.W
            tile.x < target.x -> Direction8.E
            tile.y > target.y -> Direction8.N
            tile.y < target.y -> Direction8.S
            else -> throw IllegalStateException("Dir is null for $tile and target $target")
        }
    }

    override fun isAllowedPlay(tile: TTBase): Boolean {
        if (emptyTile == null) {
            val allowedOwner = !tile.isWon || tile.wonBy == this.currentPlayer
            return isBorder(tile) && allowedOwner
        }
        if (tile == emptyTile || !isBorder(tile)) {
            return false
        }

        val dir = direction(tile)
        val parent = tile.parent!!
        val otherSide = parent.getSub(tile.x - dir.deltaX, tile.y - dir.deltaY)
        if (otherSide != null) {
            return false
        }

        var curr: TTBase? = tile
        while (curr != null) {
            curr = parent.getSub(curr.x + dir.deltaX, curr.y + dir.deltaY)
            if (curr == emptyTile) {
                return true
            }
        }
        return false
    }

    override fun onReset() {
        this.game.subs().forEach { it.setPlayedBy(TTPlayer.NONE) }
        this.emptyTile = null
    }

    override fun performPlay(tile: TTBase): Boolean {
        if (!isAllowedPlay(tile)) {
            return false
        }
        if (emptyTile == null) {
            emptyTile = tile
            tile.setPlayedBy(TTPlayer.NONE)
            return true
        }
        val target = emptyTile!!
        val dir = direction(tile)
        var sub: TTBase? = target
        while (sub != tile && sub != null) {
            val parent = sub.parent!!
            val next = parent.getSub(sub.x + dir.deltaX*-1, sub.y + dir.deltaY*-1)
            if (next != null) {
                sub.setPlayedBy(next.wonBy)
            }
            sub = next
        }
        tile.setPlayedBy(this.currentPlayer)
        this.emptyTile = null
        this.nextPlayer()
        this.game.determineWinner()
        return true
    }
}

class DslTTT {
    val playAction = createActionType("play", Point::class)
    val game = createGame<TTController>("TTT") {
        val grid = gridSpec<TTBase> {
            size(model.game.sizeX, model.game.sizeY)
            getter { x, y -> model.game.getSub(x, y)!! }
        }
        setup(TTOptions::class) {
            defaultConfig {
                TTOptions(3, 3, 3)
            }
            init {conf ->
                TTClassicController(TTFactories().classicMNK(conf!!.m, conf.n, conf.k))
            }
        }
        logic(ttLogic(grid))
        view(ttView(grid))
    }

    val gameQuixo = createGame<TTController>("Quixo") {
        val grid = gridSpec<TTBase> {
            size(model.game.sizeX, model.game.sizeY)
            getter { x, y -> model.game.getSub(x, y)!! }
        }
        setup(TTOptions::class) {
            defaultConfig { TTOptions(5, 5, 5) }
            init { conf -> TTQuixoController(TTFactories().classicMNK(conf!!.m, conf.n, conf.k)) }
        }
        logic(ttLogic(grid))
        view(ttView(grid))
    }

    val gameConnect4 = createGame<TTController>("Connect4") {
        val grid = gridSpec<TTBase> {
            size(model.game.sizeX, model.game.sizeY)
            getter { x, y -> model.game.getSub(x, y)!! }
        }
        setup(TTOptions::class) {
            defaultConfig {
                TTOptions(7, 6, 4)
            }
            init {conf ->
                TTClassicControllerWithGravity(TTFactories().classicMNK(conf!!.m, conf.n, conf.k))
            }
        }
        logic(ttLogic(grid))
        view(ttView(grid))
    }

    val gameUTTT = createGame<TTController>("UTTT") {
        val grid = gridSpec<TTBase> {
            size(model.game.sizeX * model.game.getSub(0, 0)!!.sizeX,
                model.game.sizeY * model.game.getSub(0, 0)!!.sizeY)
            getter { x, y -> model.game.getSmallestTile(x, y)!! }
        }
        setup(TTOptions::class) {
            defaultConfig {
                TTOptions(3, 3, 3)
            }
            init {conf ->
                TTUltimateController(TTFactories().ultimateMNK(conf!!.m, conf.n, conf.k))
            }
        }
        logic(ttLogic(grid))
        view {
            currentPlayer { it.currentPlayer.index() }
            winner(winner)
            value("boards") {e ->
                e.game.subs().chunked(3).map {areas ->
                    areas.map {area ->
                        val chunkedSubs = area.subs().chunked(3).map {tiles ->
                            tiles.map { tile ->
                                mapOf("owner" to tile.wonBy.index().takeIf { i -> i >= 0 })
                            }
                        }
                        mapOf("owner" to area.wonBy.index().takeIf { i -> i >= 0 },
                            "subs" to chunkedSubs)
                    }
                }
            }
            value("activeBoard") {
                val active = (it as TTUltimateController).activeBoard ?: return@value null
                mapOf("x" to active.x, "y" to active.y)
            }
        }
    }

    val gameReversi = createGame<TTController>("Reversi") {
        val grid = gridSpec<TTBase> {
            size(model.game.sizeX, model.game.sizeY)
            getter { x, y -> model.game.getSmallestTile(x, y)!! }
        }
        setup(Unit::class) {
            defaultConfig { Unit }
            init { TTOthello(8) }
        }
        logic(ttLogic(grid))
        view(ttView(grid))
    }

    private val winner: (TTController) -> Int? = {
        when {
            it.isGameOver -> it.wonBy.index()
            isPlacesLeft(it.game) -> null
            else -> -1 // TODO: Make a better way to represent 'draw'
        }
    }

    private fun ttView(grid: GridDsl<TTController, TTBase>): GameViewDsl<TTController> = {
        currentPlayer { it.currentPlayer.index() }
        winner(winner)
        grid("board", grid) {
            owner { it.wonBy.index().takeIf {n -> n >= 0 } }
        }
    }

    private fun ttLogic(grid: GridDsl<TTController, TTBase>): GameLogicDsl<TTController> = {
        winner(winner)
        action2D(playAction, grid) {
            allowed { it.playerIndex == it.game.currentPlayer.index() && it.game.isAllowedPlay(it.target) }
            effect {
                it.game.play(it.target)
            }
        }
    }

    private fun isPlacesLeft(tt: TTBase): Boolean {
        if (!tt.hasSubs()) {
            return !tt.isWon
        }
        return !tt.isWon && (0 until tt.sizeY).asSequence().flatMap { y ->
            (0 until tt.sizeX).asSequence().map { x ->
                tt.getSub(x, y)!!
            }
        }.any { isPlacesLeft(it) }
    }

}
