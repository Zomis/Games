package net.zomis.games

import net.zomis.best

interface PlayerEliminationsRead {
    val playerCount: Int
    val playerIndices: IntRange get() = 0 until playerCount
    fun remainingPlayers(): List<Int>
    fun eliminations(): List<PlayerElimination>
    fun nextPlayer(currentPlayer: Int): Int?
    fun nextEliminationPosition(winResult: WinResult): Int
    fun eliminationFor(playerIndex: Int): PlayerElimination? = eliminations().find { it.playerIndex == playerIndex }
    fun isAlive(playerIndex: Int) = eliminationFor(playerIndex) == null
    fun isGameOver(): Boolean
    fun isEliminated(playerIndex: Int): Boolean = !isAlive(playerIndex)
}
interface PlayerEliminationsWrite: PlayerEliminationsRead {
    fun result(playerIndex: Int, winResult: WinResult)
    fun position(playerIndex: Int, winResult: WinResult, position: Int)
    fun eliminate(elimination: PlayerElimination)
    fun eliminateMany(playerIndices: Iterable<Int>, winResult: WinResult)

    fun eliminateRemaining(winResult: WinResult)
    fun <T> eliminateBy(playersAndScores: List<Pair<Int, T>>, comparator: Comparator<T>)
    fun singleWinner(playerIndex: Int) {
        require(playerIndex in (0 until playerCount)) { "PlayerIndex must be within range (0 until $playerCount)" }
        this.result(playerIndex, WinResult.WIN)
        this.eliminateRemaining(WinResult.LOSS)
    }
}
@Deprecated("replace with PlayerEliminationsWrite or PlayerEliminationsRead")
interface PlayerEliminationCallback: PlayerEliminationsWrite

data class PlayerElimination(val playerIndex: Int, val winResult: WinResult, val position: Int)

class PlayerEliminations(override val playerCount: Int): PlayerEliminationCallback, PlayerEliminationsWrite, PlayerEliminationsRead {

    private val eliminations = mutableListOf<PlayerElimination>()
    var callback: (PlayerElimination) -> Unit = {}

    override fun remainingPlayers(): List<Int> {
        return (0 until playerCount).filter {
            playerIndex -> eliminations.none { it.playerIndex == playerIndex }
        }
    }

    override fun eliminations(): List<PlayerElimination> {
        return this.eliminations.toList()
    }

    override fun result(playerIndex: Int, winResult: WinResult) {
        val position = this.nextEliminationPosition(winResult)
        this.position(playerIndex, winResult, position)
    }

    override fun nextEliminationPosition(winResult: WinResult): Int {
        var position = if (winResult == WinResult.LOSS) playerCount + 1 else 0
        do {
            position += if (winResult == WinResult.LOSS) -1 else +1
        } while (eliminations.any { elim -> elim.position == position })
        return position
    }

    override fun eliminateMany(playerIndices: Iterable<Int>, winResult: WinResult) {
        val position = this.nextEliminationPosition(winResult)
        playerIndices.map { PlayerElimination(it, winResult, position) }.forEach(this::eliminate)
    }

    override fun position(playerIndex: Int, winResult: WinResult, position: Int) {
        this.eliminate(PlayerElimination(playerIndex, winResult, position))
    }

    override fun eliminate(elimination: PlayerElimination) {
        val previousElimination = eliminations.firstOrNull { it.playerIndex == elimination.playerIndex }
        if (previousElimination != null) {
            throw IllegalArgumentException("Player is already eliminated: $previousElimination. Unable to eliminate $elimination")
        }
        if (elimination.position <= 0) {
            throw IllegalArgumentException("Elimination position must be positive, but is ${elimination.position}")
        }
        if (elimination.position > playerCount) {
            throw IllegalArgumentException("Elimination position ${elimination.position} must be less than or equal to playerCount $playerCount")
        }
        this.eliminations.add(elimination)
        callback(elimination)
    }

    override fun eliminateRemaining(winResult: WinResult) {
        val position = this.nextEliminationPosition(winResult)
        val newEliminations = remainingPlayers().map {
            PlayerElimination(it, winResult, position)
        }
        for (elimination in newEliminations) eliminate(elimination)
    }

    override fun <T> eliminateBy(playersAndScores: List<Pair<Int, T>>, comparator: Comparator<T>) {
        val pairComparator = object : Comparator<Pair<Int, T>> {
            override fun compare(a: Pair<Int, T>, b: Pair<Int, T>): Int {
                return comparator.compare(a.second, b.second)
            }
        }
        var playersAndScoresRemaining = playersAndScores
        var position = this.nextEliminationPosition(WinResult.WIN)
        while (playersAndScoresRemaining.isNotEmpty()) {
            val nextBatch = playersAndScoresRemaining.best(pairComparator)
            nextBatch.forEach {
                val winResult = when {
                    nextBatch.size == playersAndScores.size -> WinResult.DRAW
                    playersAndScoresRemaining.size == playersAndScores.size -> WinResult.WIN
                    playersAndScoresRemaining.size == nextBatch.size -> WinResult.LOSS
                    else -> WinResult.DRAW
                }
                this.eliminate(PlayerElimination(it.first, winResult, position))
            }
            position += nextBatch.size
            playersAndScoresRemaining = playersAndScoresRemaining.minus(nextBatch)
        }
    }

    override fun nextPlayer(currentPlayer: Int): Int? {
        val remaining = this.remainingPlayers().sorted()
        return remaining.firstOrNull { it > currentPlayer } ?: remaining.firstOrNull()
    }

    override fun isGameOver(): Boolean {
        return this.eliminations.map { it.playerIndex }.distinct().size == playerCount
    }

}
