package net.zomis.games.server2.ais.gamescorers

import net.zomis.games.impl.ArtaxGame
import net.zomis.games.impl.TTArtax
import net.zomis.games.server2.ais.ScorerAIFactory
import net.zomis.games.server2.ais.scorers.ScorerFactory
import net.zomis.tttultimate.Direction8

object ArtaxScorers {

    val scorers = ScorerFactory<TTArtax>()
    val artaxTake = scorers.action(ArtaxGame.moveAction) {
        val pm = action.parameter
        val board = model.board
        val neighbors = Direction8.values()
                .map { board.point(pm.destination.x + it.deltaX, pm.destination.y + it.deltaY) }
                .mapNotNull { it.rangeCheck(board) }
                .count { it.value != action.playerIndex && it.value != null }
        neighbors.toDouble()
    }
    val copying = scorers.action(ArtaxGame.moveAction) {
        action.parameter.let {
            -it.destination.minus(it.source).abs().distance()
        }
    }

    fun ais() = listOf(
        ScorerAIFactory("Artax", "#AI_Aggressive_Simple", artaxTake),
        ScorerAIFactory("Artax", "#AI_Aggressive_Defensive", copying, artaxTake.weight(0.35)),
        ScorerAIFactory("Artax", "#AI_Defensive", copying.weight(2), artaxTake.weight(0.35))
    )

}
