package net.zomis.games.impl.minesweeper.ais

import net.zomis.games.impl.minesweeper.Flags
import net.zomis.games.impl.minesweeper.Weapons
import net.zomis.games.scorers.ScorerContext
import net.zomis.games.scorers.ScorerFactory
import net.zomis.minesweeper.analyze.detail.DetailedResults

object OpenFieldProbability {

    fun scorer(
        scorers: ScorerFactory<Flags.Model>,
        advancedAnalysis: (ScorerContext<Flags.Model>) -> DetailedResults<Flags.Field>?
    ) = run {
        scorers.action(Flags.useWeapon) {
            if (action.parameter.weapon !is Weapons.Default) return@action 0.0
            val field = action.game.fieldAt(action.parameter.position)
            val data = require(advancedAnalysis)?.getProxyFor(field) ?: return@action 0.0
            -data.probabilities[0]
        }
    }
}