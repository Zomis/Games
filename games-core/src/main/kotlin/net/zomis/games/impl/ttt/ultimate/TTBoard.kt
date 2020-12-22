package net.zomis.games.impl.ttt.ultimate

data class TTMNKParameters(val width: Int, val height: Int, val consecutiveRequired: Int)

/**
 * Interface for classes that can contain other objects, 'subs', in a rectangular way
 *
 * @param <T> Type of sub
</T> */
interface HasSub<out T> : Winnable {
    val winConds: Iterable<TTWinCondition>
    val sizeX: Int
    val sizeY: Int
    val consecutiveRequired: Int
    fun getSub(x: Int, y: Int): T?
    fun hasSubs(): Boolean
}
typealias TicFactory = (parent: TTBase, x: Int, y: Int) -> TTBase
object TicUtils {
    /**
     * Get which board a tile will send the opponent to (in a TTTUltimate context)
     *
     * @param tile The tile to be played
     * @return The board which the tile directs to
     */
    fun getDestinationBoard(tile: TTBase): TTBase? {
        val parent = tile.parent ?: return null
        val grandpa = parent.parent ?: return null
        return grandpa.getSub(tile.x, tile.y)
    }

    /**
     * Find the win conditions which contains a specific field
     *
     * @param field The field to look for
     * @param board Where to look for win conditions
     * @return A collection which only contains win conditions which contains the field
     */
    fun <E : Winnable> getWinCondsWith(field: E, board: HasSub<E>): Collection<TTWinCondition> {
        val coll = mutableListOf<TTWinCondition>()
        for (cond in board.winConds) {
            if (cond.hasWinnable(field)) {
                coll.add(cond)
            }
        }
        return coll
    }

    /**
     * Get all smaller tiles/boards in a board
     *
     * @param board Board to scan
     * @return Collection of all smaller tiles/boards contained in board.
     */
    fun <T> getAllSubs(board: HasSub<T>): Collection<T> {
        val list = mutableListOf<T>()
        val sizeX = board.sizeX
        val sizeY = board.sizeY
        for (x in 0 until sizeX) {
            for (y in 0 until sizeY) {
                list.add(board.getSub(x, y)!!)
            }
        }
        return list
    }

    /**
     * Recursively scan for smaller subs
     *
     * @param game The outermost object to scan
     * @return A collection containing all fields within the specified 'game' which do not have any subs
     */
    fun getAllSmallestFields(game: TTBase): Collection<TTBase> {
        val all = mutableListOf<TTBase>()

        for (sub in getAllSubs(game)) {
            if (sub.hasSubs()) {
                all.addAll(getAllSmallestFields(sub))
            } else {
                all.add(sub)
            }
        }
        return all
    }

    /**
     * Create win conditions
     *
     * @param board The board to create win conditions for
     * @return A list of all WinConditions that was created
     */
    fun setupWins(board: HasSub<Winnable>): List<TTWinCondition> {
        if (!board.hasSubs()) {
            val list = mutableListOf<TTWinCondition>()
            list.add(TTWinCondition(board))
            return list
        }

        val consecutive = board.consecutiveRequired
        val conds = mutableListOf<TTWinCondition>()

        // Scan columns for a winner
        for (xx in 0 until board.sizeX) {
            newWin(conds, consecutive, loopAdd(board, xx, 0, 0, 1))
        }

        // Scan rows for a winner
        for (yy in 0 until board.sizeY) {
            newWin(conds, consecutive, loopAdd(board, 0, yy, 1, 0))
        }

        // Scan diagonals for a winner: Bottom-right
        for (yy in 0 until board.sizeY) {
            newWin(conds, consecutive, loopAdd(board, 0, yy, 1, 1))
        }
        for (xx in 1 until board.sizeX) {
            newWin(conds, consecutive, loopAdd(board, xx, 0, 1, 1))
        }

        // Scan diagonals for a winner: Bottom-left
        for (xx in 0 until board.sizeX) {
            newWin(conds, consecutive, loopAdd(board, xx, 0, -1, 1))
        }
        for (yy in 1 until board.sizeY) {
            newWin(conds, consecutive, loopAdd(board, board.sizeX - 1, yy, -1, 1))
        }

        return conds
    }

    private fun newWin(conds: MutableList<TTWinCondition>, consecutive: Int, winnables: List<Winnable>) {
        // shorter win conditions doesn't need to be added as they will never be able to win
        if (winnables.size >= consecutive) {
            conds.add(TTWinCondition(winnables, consecutive))
        }
    }

    private fun loopAdd(board: HasSub<Winnable>,
                        xxStart: Int, yyStart: Int, dx: Int, dy: Int): List<Winnable> {
        var xx = xxStart
        var yy = yyStart
        val winnables = mutableListOf<Winnable>()

        var tile: Winnable?
        do {
            tile = board.getSub(xx, yy)
            xx += dx
            yy += dy
            if (tile != null)
                winnables.add(tile)
        } while (tile != null)

        return winnables
    }

}
class TTBase(val parent: TTBase?, val x: Int, val y: Int,
             val mnkParameters: TTMNKParameters, factory: TicFactory?) : Winnable, HasSub<TTBase> {

    private val subs: Array<Array<TTBase>>

    override val winConds: List<TTWinCondition>

    override var wonBy: TTPlayer = TTPlayer.NONE

    override val sizeX: Int
        get() = this.mnkParameters.width

    override val sizeY: Int
        get() = this.mnkParameters.height

    override val consecutiveRequired: Int
        get() = this.mnkParameters.consecutiveRequired

    val isWon: Boolean
        get() = wonBy !== TTPlayer.NONE

    val globalX: Int
        get() {
            if (parent == null) {
                return 0
            }
            return if (parent.parent == null) x else parent.x * parent.parent.sizeX + this.x
        }

    val globalY: Int
        get() {
            if (parent == null) {
                return 0
            }
            return if (parent.parent == null) y else parent.y * parent.parent.sizeY + this.y
        }

    constructor(parent: TTBase?, parameters: TTMNKParameters, factory: TicFactory) : this(parent, 0, 0, parameters, factory)

    init {
        this.subs = Array(mnkParameters.height) { yy ->
            Array(mnkParameters.width) { xx ->
                factory!!.invoke(this, xx, yy)
            }
        }
        this.winConds = TicUtils.setupWins(this)
    }

    fun determineWinner() {
        var winner: TTPlayer = TTPlayer.NONE
        for (cond in this.winConds) {
            winner = winner.or(cond.determineWinnerNew())
        }
        if (winner == TTPlayer.NONE && subs().all { it.isWon }) {
            winner = TTPlayer.BLOCKED
        }
        this.wonBy = winner
    }

    fun subs(): List<TTBase> {
        if (!hasSubs()) {
            return emptyList()
        }
        return subs.flatMap { it.toList() }
    }

    override fun getSub(x: Int, y: Int): TTBase? {
        if (!hasSubs() && x == 0 && y == 0) {
            return this
        }
        if (x < 0 || y < 0) {
            return null
        }
        return if (x >= sizeX || y >= sizeY) null else subs[y][x]
    }

    fun setPlayedBy(playedBy: TTPlayer) {
        this.wonBy = playedBy
    }

    override fun hasSubs(): Boolean {
        return sizeX != 0 && sizeY != 0
    }

    override fun toString(): String {
        return "{Pos $x, $y; Size $sizeX, $sizeY; Played by $wonBy. Parent is $parent}"
    }

    fun reset() {
        this.setPlayedBy(TTPlayer.NONE)
        subs().forEach { it.reset() }
    }

    fun getSmallestTile(x: Int, y: Int): TTBase? {
        val topLeft = getSub(0, 0) ?: return null
        val grandParent = topLeft.hasSubs()

        if (!grandParent) {
            return this.getSub(x, y)
        }

        val subX = x / topLeft.sizeX
        val subY = y / topLeft.sizeY
        val board = getSub(subX, subY) ?: throw NullPointerException("No such smallest tile found: $x, $y")
        return board.getSub(x - subX * sizeX, y - subY * sizeY)
    }

}

class TTFactories {

    fun factory(mnk: TTMNKParameters, next: TicFactory): TicFactory {
        return {parent, x, y -> TTBase(parent, x, y, mnk, next) }
    }

    fun classicMNK(width: Int, height: Int, consecutive: Int): TTBase {
        return TTBase(null, TTMNKParameters(width, height, consecutive), lastFactory)
    }

    fun classicMNK(mnk: Int): TTBase {
        return classicMNK(mnk, mnk, mnk)
    }

    fun ultimate(mnk: Int = 3): TTBase {
        return ultimateMNK(mnk, mnk, mnk)
    }

    fun ultimateMNK(width: Int, height: Int, consecutive: Int): TTBase {
        return TTBase(null, TTMNKParameters(width, height, consecutive), areaFactory)
    }

    fun othello(size: Int): TTBase {
        return TTBase(null, TTMNKParameters(size, size, size + 1), lastFactory)
    }

    private val mnkEmpty = TTMNKParameters(0, 0, 0)

    private val lastFactory: TicFactory = {parent, x, y ->
        TTBase(parent, x, y, mnkEmpty, null)
    }

    private val areaFactory: TicFactory = {parent, x, y ->
        TTBase(parent, x, y, parent.mnkParameters, lastFactory)
    }

}

interface TTMoveListener {
    fun onMove(playedAt: TTBase)
}

enum class TTPlayer {

    /**
     * No player
     */
    NONE,
    X,
    O,
    /**
     * Both players has succeeded
     */
    XO,
    /**
     * Neither player (both have tried, none has succeeded)
     */
    BLOCKED,
    ;

    val isExactlyOnePlayer: Boolean
        get() = this == X || this == O

    operator fun next(): TTPlayer {
        if (!isExactlyOnePlayer) {
            throw UnsupportedOperationException("Only possible to call .next() on a real player but it was called on " + this)
        }
        return if (this == X) O else X
    }

    /**
     * Determine if this player is (also) another player.<br></br>
     * This is the same as `this.and(other) == other`
     *
     * @param other
     * @return
     */
    fun `is`(other: TTPlayer): Boolean {
        return this.and(other) == other
    }

    fun and(other: TTPlayer): TTPlayer {
        if (this == NONE || other == NONE) {
            return NONE
        }
        if (isExactlyOnePlayer && other.isExactlyOnePlayer) {
            return if (this == other) this else NONE
        }
        if (this == BLOCKED || other == BLOCKED) {
            return other
        }
        return if (this == XO) other else other.and(this)
    }

    fun or(other: TTPlayer): TTPlayer {
        if (this == NONE) {
            return other
        }
        if (other == NONE) {
            return this
        }
        if (this == XO) {
            return this
        }
        if (this == BLOCKED || other == BLOCKED) {
            return BLOCKED
        }
        return if (this != other) XO else this
    }

    companion object {
        fun isExactlyOnePlayer(winner: TTPlayer?): Boolean {
            return winner != null && winner.isExactlyOnePlayer
        }
    }

}

class TTWinCondition : Iterable<Winnable> {

    private val winnables: List<Winnable>
    private val consecutive: Int

    constructor(vararg winnables: Winnable) : this(winnables.toList())

    constructor(winnables: List<Winnable>) : this(winnables, winnables.size)

    constructor(winnables: List<Winnable>, consecutive: Int) {
        if (winnables.isEmpty()) {
            throw IllegalArgumentException("Can't have an empty win condition!")
        }
        this.winnables = winnables.toMutableList()
        this.consecutive = consecutive
    }

    fun neededForWin(player: TTPlayer): Int {
        return winnables.size - hasCurrently(player)
    }

    fun isWinnable(byPlayer: TTPlayer): Boolean {
        return hasCurrently(byPlayer.next()) == 0
    }

    fun hasCurrently(player: TTPlayer): Int {
        var i = 0
        for (winnable in winnables) {
            if (winnable.wonBy.and(player) == player) {
                i++
            }
        }
        return i
    }

    fun determineWinnerNew(): TTPlayer {
        var winner: TTPlayer = TTPlayer.NONE

        val consecutivePlayers = IntArray(TTPlayer.values().size)
        for (winnable in winnables) {
            val current = winnable.wonBy
            for (pl in TTPlayer.values()) {
                val i = pl.ordinal
                if (pl.and(current) == pl) {
                    consecutivePlayers[i]++
                } else {
                    consecutivePlayers[i] = 0
                }

                if (consecutivePlayers[i] >= this.consecutive) {
                    winner = winner.or(pl)
                }
            }
        }
        return winner
    }

    fun hasWinnable(field: Winnable): Boolean {
        return winnables.contains(field)
    }

    fun size(): Int {
        return winnables.size
    }

    override fun iterator(): Iterator<Winnable> {
        return this.winnables.toMutableList().iterator()
    }

}

interface Winnable {

    val wonBy: TTPlayer

}
