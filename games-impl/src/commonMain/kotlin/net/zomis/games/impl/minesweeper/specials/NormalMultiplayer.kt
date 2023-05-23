package net.zomis.games.impl.minesweeper.specials

import net.zomis.games.PlayerEliminationsRead
import net.zomis.games.PlayerEliminationsWrite
import net.zomis.games.WinResult
import net.zomis.games.impl.minesweeper.Flags

class NormalMultiplayer {

    object Goal {

        fun endShowMines(eliminations: PlayerEliminationsRead, game: Flags.Model) {
            if (!eliminations.isGameOver()) return
            game.grid.all().filter { !it.value.clicked && it.value.isMine() }.forEach {field ->
                field.value.reveal(playedBy = null)
            }
        }

        fun lastPlayersStanding(eliminations: PlayerEliminationsWrite, count: Int) {
            val winnablePlayers = eliminations.remainingPlayers().count()
            if (winnablePlayers <= count) {
                eliminations.eliminateRemaining(WinResult.WIN)
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