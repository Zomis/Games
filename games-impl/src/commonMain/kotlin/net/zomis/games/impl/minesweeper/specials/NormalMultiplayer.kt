package net.zomis.games.impl.minesweeper.specials

import net.zomis.games.PlayerEliminations
import net.zomis.games.PlayerEliminationsWrite
import net.zomis.games.WinResult
import net.zomis.games.dsl.impl.Game
import net.zomis.games.impl.minesweeper.Flags

class NormalMultiplayer {

    object Goal {

        fun lastPlayersStanding(number: Int): (Game<Flags.Model>) -> Unit {
            return { e: Game<Flags.Model> ->
                val winnablePlayers = e.eliminations.remainingPlayers().count()
                if (winnablePlayers <= number) {
                    e.eliminations.eliminateRemaining(WinResult.WIN)
                }
            }
        }

        fun eliminateLosers(game: Flags.Model, eliminations: PlayerEliminationsWrite) {
            val maxScore = game.players.maxOf { it.score }
            val remainingMines = game.remainingMines()
            val losers = eliminations.remainingPlayers().map { game.players[it] }
                .filter { remainingMines + it.score < maxScore }
            losers.forEach { eliminations.result(it.playerIndex, WinResult.LOSS) }
        }
    }

}