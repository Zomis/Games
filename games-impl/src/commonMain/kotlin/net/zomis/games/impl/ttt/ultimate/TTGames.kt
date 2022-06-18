package net.zomis.games.impl.ttt.ultimate

import net.zomis.games.common.Direction8

open class TTClassicController(board: TTBase) : TTController(board) {

    override fun isAllowedPlay(tile: TTBase): Boolean {
        return tile.parent != null && !tile.hasSubs() &&
                tile.parent.wonBy == TTPlayer.NONE &&
                tile.wonBy === TTPlayer.NONE
    }

    override fun performPlay(tile: TTBase): Boolean {
        tile.setPlayedBy(currentPlayer)
        tile.parent!!.determineWinner()
        nextPlayer()
        return true
    }

    override fun onReset() {
    }

}

class TTClassicControllerWithGravity(board: TTBase) : TTClassicController(board) {

    override fun isAllowedPlay(tile: TTBase): Boolean {
        val sup = super.isAllowedPlay(tile)
        if (!sup) {
            return false
        }
        val parent = tile.parent
        return parent!!.getSub(tile.x, tile.y + 1)?.isWon ?: true
    }

}

abstract class TTController(val game: TTBase) {
    var currentPlayer = TTPlayer.X
        protected set
    private var moveListener: TTMoveListener? = null
    private var history: StringBuilder = StringBuilder()

    val CHARS = ('0'..'9') + ('a'..'z') + ('A'..'Z')

    val isGameOver: Boolean
        get() = game.isWon

    val wonBy: TTPlayer
        get() = game.wonBy

    abstract fun isAllowedPlay(tile: TTBase): Boolean

    fun play(tile: TTBase?): Boolean {
        if (tile == null) {
            throw IllegalArgumentException("Tile to play at cannot be null.")
        }

        if (!isAllowedPlay(tile)) {
            return false
        }
        if (!this.performPlay(tile)) {
            return false
        }

        this.addToHistory(tile)

        if (this.moveListener != null) {
            this.moveListener!!.onMove(tile)
        }

        return true
    }

    private fun addToHistory(tile: TTBase) {
        if (!history.isEmpty()) {
            history.append(",")
        }
        history.append(CHARS[tile.globalX])
        history.append(CHARS[tile.globalY])
    }

    protected abstract fun performPlay(tile: TTBase): Boolean

    fun play(x: Int, y: Int): Boolean {
        return this.play(game.getSmallestTile(x, y))
    }

    protected fun nextPlayer() {
        currentPlayer = currentPlayer.next()
    }

    fun setOnMoveListener(moveListener: TTMoveListener) {
        this.moveListener = moveListener
    }

    fun makeMoves(history: String) {
        for (move in history.split(",")) {
            if (move.isEmpty())
                continue
            if (move.length != 2) {
                throw IllegalArgumentException("Unexcepted move length. $move")
            }

            val x = CHARS.indexOf(move[0])
            val y = CHARS.indexOf(move[1])

            val tile = game.getSmallestTile(x, y)
            if (!this.play(tile))
                throw IllegalStateException("Unable to make a move at $x, $y: $tile")
        }
    }

    fun saveHistory(): String {
        return this.history.toString()
    }

    fun reset() {
        this.currentPlayer = TTPlayer.X
        this.history = StringBuilder()
        this.game.reset()
        this.onReset()
    }

    protected abstract fun onReset()

    open fun getViewFor(tile: TTBase): String {
        return if (tile.isWon) tile.wonBy.toString() else ""
    }
}

object TTControllers {

    fun connectFour(): TTController {
        return TTClassicControllerWithGravity(TTFactories().classicMNK(7, 6, 4))
    }

    fun classicTTT(): TTController {
        return TTClassicController(TTFactories().classicMNK(3))
    }

    fun ultimateTTT(): TTController {
        return TTUltimateController(TTFactories().ultimate())
    }

}

class TTOthello constructor(size: Int = 8) : TTController(TTFactories().othello(size)) {
    init {
        this.onReset()
    }

    override fun isAllowedPlay(tile: TTBase): Boolean {
        if (game.isWon) {
            return false
        }
        if (tile.hasSubs()) {
            return false
        }
        return if (tile.isWon) false else fieldCover(tile, currentPlayer).isNotEmpty()
    }

    private fun fieldCover(tile: TTBase, player: TTPlayer): List<TTBase> {
        if (!player.isExactlyOnePlayer)
            throw IllegalArgumentException()

        val tt = mutableListOf<TTBase>()
        val parent = tile.parent
        for (dir in Direction8.values()) {
            var matchFound = false
            val thisDirection = mutableListOf<TTBase>()
            var loop: TTBase? = tile
            do {
                loop = parent!!.getSub(loop!!.x + dir.deltaX, loop.y + dir.deltaY)
                if (loop == null)
                    break
                if (loop.wonBy === TTPlayer.NONE)
                    break
                if (player.and(loop.wonBy) === player) {
                    matchFound = true
                }
                if (player !== loop.wonBy) {
                    thisDirection.add(loop)
                }
            } while (!matchFound)

            if (matchFound)
                tt.addAll(thisDirection)

        }
        return tt
    }

    override fun performPlay(tile: TTBase): Boolean {
        val convertingTiles = fieldCover(tile, currentPlayer)
        for (ff in convertingTiles) {
            ff.setPlayedBy(currentPlayer)
        }
        tile.setPlayedBy(currentPlayer)
        nextPlayer()
        if (!isMovePossible()) {
            nextPlayer()
            if (!isMovePossible()) {
                val x = countSquares(TTPlayer.X)
                val o = countSquares(TTPlayer.O)
                var result = TTPlayer.NONE
                if (x >= o) result = result.or(TTPlayer.X)
                if (o >= x) result = result.or(TTPlayer.O)
                game.setPlayedBy(result)
            }
        }

        return true
    }

    private fun countSquares(player: TTPlayer): Int = game.all().count { it.value.wonBy.`is`(player) }

    private fun isMovePossible(): Boolean = this.game.all().any { isAllowedPlay(it.value) }

    override fun onReset() {
        val board = this.game
        board.reset()
        val mX = board.sizeX / 2
        val mY = board.sizeY / 2
        board.getSub(mX - 1, mY - 1)!!.setPlayedBy(TTPlayer.X)
        board.getSub(mX - 1, mY)!!.setPlayedBy(TTPlayer.O)
        board.getSub(mX, mY - 1)!!.setPlayedBy(TTPlayer.O)
        board.getSub(mX, mY)!!.setPlayedBy(TTPlayer.X)
    }

}

class TTQuantumController : TTController(TTFactories().ultimate()) {

    // http://en.wikipedia.org/wiki/Quantum_tic_tac_toe
    // TODO: Replay http://www.zomis.net/ttt/TTTWeb.html?mode=Quantum&history=22,23,25,26,27,37,57,67,66,65,64,62,61,52,51,60,51,43 -- no moves available

    private val subscripts = mutableMapOf<TTBase, Int>()
    private var firstPlaced: TTBase? = null
    private var collapse: Int? = null
    private var counter: Int = 0

    init {
        this.onReset()
    }

    override fun isAllowedPlay(tile: TTBase): Boolean {
        if (collapse == null && tile.isWon) {
            return false
        }

        if (collapse != null) {
            return subscripts[tile] === collapse
        }

        return if (firstPlaced != null) { // x Play two moves before switching, not on the same board
            tile.parent != firstPlaced
        } else !tile.isWon && !tile.parent!!.isWon
    }

    override fun performPlay(tile: TTBase): Boolean {
        if (collapse != null) {
            collapse = null
            // The new player should choose a field that should be collapsed
            performCollapse(tile)
            game.determineWinner()
            if (game.wonBy === TTPlayer.XO) {
                game.setPlayedBy(tieBreak())
            }
            return true
        }

        tile.setPlayedBy(currentPlayer)
        subscripts.put(tile, counter)

        if (firstPlaced != null) {
            firstPlaced = null
            nextPlayer()
            if (isEntaglementCycleCreated(tile)) {
                collapse = counter
                // when a cycle has been created the next player must choose the field that should be collapsed
            }
            counter++
        } else
            firstPlaced = tile.parent

        return true
    }

    private fun tieBreak(): TTPlayer {
        var lowestWin: TTWinCondition? = null
        for (cond in game.winConds) {
            val pl = cond.determineWinnerNew()
            if (pl === TTPlayer.NONE)
                continue
            if (lowestWin == null || highestSubscript(cond) < highestSubscript(lowestWin))
                lowestWin = cond
        }
        return lowestWin!!.determineWinnerNew()
    }

    private fun highestSubscript(cond: TTWinCondition): Int {
        var highest = 0
        for (tile in cond) {
            val value = subscripts[tile] ?: throw NullPointerException("Position doesn't have a subscript: $cond")
            highest = if (highest >= value) highest else value
        }
        return highest
    }


    private fun performCollapse(tile: TTBase) {
        if (!tile.isWon)
            throw IllegalArgumentException("Cannot collapse tile $tile")
        if (tile.hasSubs())
            throw AssertionError(subscripts.toString())
        if (tile.parent!!.isWon)
            throw AssertionError()

        val tangled = findEntanglement(tile)
        if (tangled != null) {
            subscripts.remove(tangled)
            tangled.reset()
        }

        val winner = tile.wonBy
        val value = subscripts.remove(tile)!!

        for (ff in TicUtils.getAllSubs(tile.parent)) {
            subscripts.remove(ff)
        }
        // TODO: Send ViewEvent to allow view to show what is happening step-by-step. Technically, this code should remove all but the tile itself.
        tile.parent.reset()
        tile.parent.setPlayedBy(winner)
        // then, when the winner has been declared, the smaller tile can be removed
        subscripts.put(tile.parent, value)
        collapseCheck()
    }

    private fun isEntaglementCycleCreated(tile: TTBase, scannedAreas: MutableSet<TTBase> = HashSet(), scannedTiles: MutableSet<TTBase> = HashSet()): Boolean {
        if (tile.parent == null || tile.hasSubs())
            throw IllegalArgumentException()

        scannedTiles.add(tile)
        if (scannedAreas.contains(tile.parent)) {
            return true
        }
        scannedAreas.add(tile.parent)

        val area = tile.parent

        val subs = TicUtils.getAllSubs(area)
        for (sub in subs) {
            if (sub == tile) continue
            if (!sub.isWon) continue
            if (scannedTiles.contains(sub)) return true

            scannedTiles.add(sub)
            val tangled = findEntanglement(sub)
            if (tangled != null) {
                val recursive = isEntaglementCycleCreated(tangled, scannedAreas, scannedTiles)
                if (recursive) return true
            }
        }
        return false
    }

    private fun collapseCheck() {
        // TEST: When a field does not have an entanglement anymore, collapse it

        for (ee in this.subscripts.entries) {
            // remove those that should be removed first, to make a clean scan later
            if (!ee.key.isWon) {
                subscripts.remove(ee.key)
            }
        }

        for (ee in this.subscripts.entries) {
            if (ee.key.hasSubs()) {
                continue
            }
            val match = findEntanglement(ee.key)
            if (match == null) {
                performCollapse(ee.key)
                collapseCheck()
                return
            }
        }
    }

    private fun findEntanglement(key: TTBase): TTBase? {
        if (!subscripts.containsKey(key))
            return null
        val match = subscripts[key]
        for (ee in this.subscripts.entries) {
            if (ee.key == key) {
                continue
            }
            if (ee.value == match) {
                return ee.key
            }
        }
        return null
    }

    override fun onReset() {
        this.subscripts.clear()
        this.collapse = null
        this.firstPlaced = null
        this.counter = 1
    }

    override fun getViewFor(tile: TTBase): String {
        var tileParent: TTBase? = tile
        if (!tileParent?.isWon!! && tileParent.parent!!.isWon) {
            tileParent = tileParent.parent
        }
        val sub = subscripts[tileParent]
        return super.getViewFor(tile) + (sub ?: "")
    }

}

class TTUltimateController(board: TTBase) : TTController(board) {
    var activeBoard: TTBase? = null
        private set

    override fun isAllowedPlay(tile: TTBase): Boolean {
        val area = tile.parent ?: return false
        val game = tile.parent.parent

        if (tile.wonBy != TTPlayer.NONE) {
            return false
        }
        if (area.wonBy.isExactlyOnePlayer) {
            return false
        }
        return if (game!!.isWon) {
            false
        } else {
            activeBoard == null || activeBoard == area || activeBoard!!.wonBy !== TTPlayer.NONE
        }
    }

    override fun performPlay(tile: TTBase): Boolean {
        tile.setPlayedBy(currentPlayer)
        activeBoard = TicUtils.getDestinationBoard(tile)
        nextPlayer()

        // Check for win condition on tile and if there is a win, cascade to it's parents
        var playAt: TTBase? = tile
        do {
            playAt?.determineWinner()
            playAt = if (playAt!!.isWon) playAt.parent else null
        } while (playAt != null)

        return true
    }

    override fun onReset() {
        this.activeBoard = null
    }
}
