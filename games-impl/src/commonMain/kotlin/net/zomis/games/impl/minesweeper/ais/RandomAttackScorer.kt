package net.zomis.games.impl.minesweeper.ais

import net.zomis.games.impl.minesweeper.Flags
import net.zomis.games.impl.minesweeper.MfeProbabilityProvider
import net.zomis.games.impl.minesweeper.WeaponUse
import net.zomis.games.scorers.Scorer
import net.zomis.games.scorers.ScorerFactory
import net.zomis.games.scorers.ScorerScope

object RandomAttackScorer {

    fun scorer(factory: ScorerFactory<Flags.Model>, provider: MfeProbabilityProvider): Scorer<Flags.Model, Any> {
        return factory.action(Flags.useWeapon) {
            require(provider)
            if (workWithWeapon(this)) scoreFor(RandomAttackScorer::getScoreFor) else 0.0
        }
    }

    fun workWithWeapon(scores: ScorerScope<Flags.Model, WeaponUse>): Boolean {
        return scores.weaponIsClick()
    }

    fun getScoreFor(field: Flags.Field): Double {
        if (ZomisTools.isZomisOpenField(field)) {
            for (ff in field.inverseNeighbors) {
                if (ff.isDiscoveredMine()) return 1.0
            }
        }
        return 0.0
    }
}
