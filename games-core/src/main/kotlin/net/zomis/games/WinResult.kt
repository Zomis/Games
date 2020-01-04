package net.zomis.games

enum class WinResult(val result: Double) {
    WIN(1.0),
    DRAW(0.0),
    LOSS(-1.0);

    fun isWinner(): Boolean {
        return this == WIN
    }

    companion object {
        fun <T> forWinner(winner: T, me: T): WinResult {
            return if (winner == me) WIN else LOSS
        }
    }
}
