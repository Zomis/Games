package net.zomis.games.server2.ais.gamescorers

import net.zomis.games.common.Direction8
import net.zomis.games.dsl.GamesImpl
import net.zomis.games.impl.ArtaxGame

object ArtaxScorers {

    val scorers = GamesImpl.game(ArtaxGame.gameArtax).scorers()
    val artaxTake = scorers.action(ArtaxGame.moveAction) {
        val pm = action.parameter
        val board = model.board
        val neighbors = Direction8.values()
                .map { board.point(pm.destination.x + it.deltaX, pm.destination.y + it.deltaY) }
                .mapNotNull { it.rangeCheck(board) }
                .count { it.valueOrNull() != action.playerIndex && it.valueOrNull() != null }
        neighbors.toDouble()
    }
    val copying = scorers.action(ArtaxGame.moveAction) {
        action.parameter.let {
            -it.destination.minus(it.source).abs().distance()
        }
    }

    fun ais() = listOf(
        scorers.ai("#AI_Aggressive_Simple", artaxTake),
        scorers.ai("#AI_Aggressive_Defensive", copying, artaxTake.weight(0.35)),
        scorers.ai("#AI_Defensive", copying.weight(2), artaxTake.weight(0.35))
    )

}
