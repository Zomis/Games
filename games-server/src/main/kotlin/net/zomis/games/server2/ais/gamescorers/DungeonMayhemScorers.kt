package net.zomis.games.server2.ais.gamescorers

import net.zomis.games.impl.*
import net.zomis.games.server2.ais.ScorerAIFactory
import net.zomis.games.server2.ais.scorers.Scorer
import net.zomis.games.server2.ais.scorers.ScorerFactory

object DungeonMayhemScorers {

    val scorers = ScorerFactory<DungeonMayhem>()

    fun ais(): List<ScorerAIFactory<DungeonMayhem>> {
        return listOf(
            ScorerAIFactory("Dungeon Mayhem", "#AI_Play_Again", symbolCount(DungeonMayhemSymbol.PLAY_AGAIN), anyTarget),
            ScorerAIFactory("Dungeon Mayhem", "#AI_Attack",
                symbolCount(DungeonMayhemSymbol.PLAY_AGAIN).weight(10), symbolCount(DungeonMayhemSymbol.ATTACK),
                    targetWeakShields, targetPlayerLowHealth.weight(10), anyTarget
            )
        )
    }

    fun symbolCount(symbol: DungeonMayhemSymbol) = scorers.action(DungeonMayhemDsl.play) {
        action.parameter.symbols.count { it == symbol }.toDouble()
    }
    val anyTarget = scorers.isAction(DungeonMayhemDsl.target)
    val targetWeakShields = scorers.action(DungeonMayhemDsl.target) {
        if (action.parameter.shieldCard != null)
            1 - 0.01 * action.game.players[action.parameter.player].shields[action.parameter.shieldCard!!].card.health.toDouble()
        else 0.0
    }
    val targetHighPlayerIndex = scorers.action(DungeonMayhemDsl.target) {
        1 + 0.01 * action.parameter.player.toDouble()
    }
    val targetPlayerLowHealth = scorers.action(DungeonMayhemDsl.target) {
        1 - 0.01 * action.game.players[action.parameter.player].health.toDouble()
    }

}