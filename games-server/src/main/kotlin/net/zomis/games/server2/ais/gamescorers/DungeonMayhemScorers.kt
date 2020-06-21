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

    fun symbolCount(symbol: DungeonMayhemSymbol): Scorer<DungeonMayhem, Any> = scorers.conditionalType(DungeonMayhemCard::class) {
        action.parameter.symbols.count { it == symbol }.toDouble()
    } as Scorer<DungeonMayhem, Any>
    val anyTarget = scorers.conditional { action.actionType == DungeonMayhemDsl.target.name }
    val targetWeakShields = scorers.conditionalType(DungeonMayhemTarget::class) {
        if (action.parameter.shieldCard != null)
            1 - 0.01 * action.game.players[action.parameter.player].shields[action.parameter.shieldCard!!].card.health.toDouble()
        else 0.0
    } as Scorer<DungeonMayhem, Any>
    val targetHighPlayerIndex = scorers.conditionalType(DungeonMayhemTarget::class) {
        1 + 0.01 * action.parameter.player.toDouble()
    } as Scorer<DungeonMayhem, Any>
    val targetPlayerLowHealth = scorers.conditionalType(DungeonMayhemTarget::class) {
        1 - 0.01 * action.game.players[action.parameter.player].health.toDouble()
    } as Scorer<DungeonMayhem, Any>

}