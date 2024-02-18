package net.zomis.games.ur

import net.zomis.games.Dice
import net.zomis.games.dsl.ReplayStateI

/*
// 0 = Outside
// 4 = First flower
Fxxx  Fx
xxxFxxxx
Fxxx  Fx
*/
class RoyalGameOfUr {

    constructor(piecesPerPlayer: Int = 7) {
        this.currentPlayer = 0
        this.roll = NOT_ROLLED
        this.pieces = Array(2) { IntArray(piecesPerPlayer) }
    }

    constructor(currentPlayer: Int, roll: Int, pieces: Array<IntArray>) {
        this.currentPlayer = currentPlayer
        this.roll = roll
        this.pieces = pieces.map { it.copyOf() }.toTypedArray()
    }

    var currentPlayer: Int private set
    val opponentPlayer: Int get() = (currentPlayer + 1) % 2

    var lastRoll: Int = 0
    var roll: Int = NOT_ROLLED
        private set

    private val pieces: Array<IntArray>

    // pieces[PLAYER][PIECE]

    fun isRollTime(): Boolean {
        return roll == NOT_ROLLED
    }

    val isMoveTime: Boolean
        get() = roll > 0

    val piecesCopy: Array<IntArray>
        get() = this.pieces.map { arr -> arr.copyOf() }.toTypedArray()

    val isFinished: Boolean
        get() = winner != NO_WINNER

    val winner: Int
        get() {
            for (i in this.pieces.indices) {
                if (this.pieces[i].all { v -> v == EXIT }) {
                    return i
                }
            }
            return NO_WINNER
        }

    private val dice = Dice.d(2).times(4).randomiser("roll")

    fun randomRoll(replayable: ReplayStateI): List<Int> {
        if (!isRollTime()) {
            throw IllegalStateException("Not time to roll. Current roll is $roll")
        }
        return dice.random(replayable)
    }

    fun doRoll(sum: Int): Int {
        this.lastRoll = sum
        if (canMove(sum)) {
            this.roll = sum
        } else {
            this.nextPlayer()
        }
        return sum
    }

    private fun nextPlayer() {
        this.currentPlayer = (this.currentPlayer + 1) % 2
    }

    fun canMove(roll: Int): Boolean {
        if (roll == 0) {
            return false
        }
        // Loop through player's pieces and check if they can move `this.roll` steps.
        for (i in 0 until this.pieces[currentPlayer].size) {
            val position = this.pieces[currentPlayer][i]
            val nextPosition = position + roll

            if (canMoveTo(currentPlayer, nextPosition)) {
                return true
            }
        }
        return false
    }

    private fun canMoveTo(currentPlayer: Int, nextPosition: Int): Boolean {
        if (isFinished) {
            return false
        }
        if (nextPosition == EXIT) {
            return true
        }
        if (nextPosition > EXIT) {
            return false
        }
        if (nextPosition >= 5 && nextPosition <= EXIT - 3) {
            // Shared area. Can knock out except on flower.
            if (isFlower(nextPosition)) {
                for (player in this.pieces.indices) {
                    if (playerOccupies(player, nextPosition)) {
                        return false
                    }
                }
                return true
            } else {
                return !playerOccupies(currentPlayer, nextPosition)
            }
        } else {
            // Private area
            return !playerOccupies(currentPlayer, nextPosition)
        }
    }

    fun playerOccupies(currentPlayer: Int, position: Int): Boolean {
        for (piece in 0 until this.pieces[currentPlayer].size) {
            if (this.pieces[currentPlayer][piece] == position) {
                return true
            }
        }
        return false
    }

    fun canMove(playerIndex: Int, position: Int, steps: Int): Boolean {
        return playerOccupies(playerIndex, position) && canMoveTo(playerIndex, position + steps)
    }

    fun move(playerIndex: Int, position: Int, steps: Int): Boolean {
        if (isFinished) {
            return false
        }
        if (!canMove(playerIndex, position, steps)) {
            return false
        }
        for (i in this.pieces[playerIndex].indices) {
            if (this.pieces[playerIndex][i] == position) {
                this.pieces[playerIndex][i] = position + steps
                performKnockout(playerIndex, position + steps)
                this.roll = NOT_ROLLED
                if (!isFlower(position + steps)) {
                    this.nextPlayer()
                }
                return true
            }
        }
        return false
    }

    private fun performKnockout(playerIndex: Int, position: Int) {
        val opponent = (playerIndex + 1) % this.pieces.size
        if (position <= 4 || position > 12) {
            return
        }
        if (isFlower(position)) {
            return
        }
        for (j in 0 until this.pieces[opponent].size) {
            if (this.pieces[opponent][j] == position) {
                this.pieces[opponent][j] = 0
            }
        }
    }

    fun isFlower(position: Int): Boolean {
        return position == 4 || position == 8 || position == EXIT - 1
    }

    fun canKnockout(position: Int): Boolean {
        return position > 4 && position < EXIT - 2 && !isFlower(position)
    }

    override fun toString(): String {
        return "RoyalGameOfUr{" +
                "pieces=" + pieces.contentDeepToString() +
                ", currentPlayer=" + currentPlayer +
                ", roll=" + roll +
                '}'.toString()
    }

    fun toLong(): Long {
        val cp = currentPlayer
        val op = 1 - cp

        var result: Long = 0
        val numberHome1 = pieces[cp].filter { i -> i == 0 }.count()
        val numberHome2 = pieces[op].filter { i -> i == 0 }.count()
        val numberGoal1 = pieces[cp].filter { i -> i == EXIT }.count()
        val numberGoal2 = pieces[op].filter { i -> i == EXIT }.count()
        var dice = roll - 1 // 1..4 --> 0..3
        if (isFinished) {
            dice = 0
        } else if (dice < 0 || dice >= 4) {
            throw IllegalStateException("Invalid dice value for serializing: $dice")
        }
        val p1 = piecesToArray(pieces[cp])
        val p2 = piecesToArray(pieces[op])

        result += numberHome1.toLong()
        result = result shl 3
        result += numberHome2.toLong()
        result = result shl 3
        result += numberGoal1.toLong()
        result = result shl 3
        result += numberGoal2.toLong()
        result = result shl 3

        result += dice.toLong()
        result = result shl 2
        for (b in p1) {
            result += (if (b) 1 else 0).toLong()
            result = result shl 1
        }
        for (b in p2) {
            result += (if (b) 1 else 0).toLong()
            result = result shl 1
        }
        return result
    }

    private fun piecesToArray(pieces: IntArray): BooleanArray {
        val result = BooleanArray(14)
        for (p in pieces) {
            if (p != 0 && p != 15) {
                result[p - 1] = true
            }
        }
        return result
    }

    fun toCompactString(): String {
        val str = StringBuilder()
        str.append(currentPlayer)
        val p0 = pieces[0].copyOf()
        p0.sort()

        val p1 = pieces[1].copyOf()
        p1.sort()

        for (aP0 in p0) {
            str.append(aP0)
        }
        for (aP1 in p1) {
            str.append(aP1)
        }
        return str.toString()
    }

    fun copy(): RoyalGameOfUr {
        return RoyalGameOfUr(this.currentPlayer, this.roll, piecesCopy)
    }

    companion object {
        private val NOT_ROLLED = -1
        private val NO_WINNER = -1
        val EXIT = 15
    }

}
