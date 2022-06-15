package net.zomis.games.server2.ais.gamescorers

import net.zomis.games.dsl.GamesImpl
import net.zomis.games.impl.*

object DungeonMayhemScorers {

    val scorers = GamesImpl.game(DungeonMayhemDsl.game).scorers()

    fun ais() = listOf(
            scorers.ai("#AI_Play_Again", symbolCount(DungeonMayhemSymbol.PLAY_AGAIN), anyTarget),
            scorers.ai("#AI_Attack",
                symbolCount(DungeonMayhemSymbol.PLAY_AGAIN).weight(10), symbolCount(DungeonMayhemSymbol.ATTACK),
                    targetWeakShields, targetPlayerLowHealth.weight(10), anyTarget
            )
        )

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