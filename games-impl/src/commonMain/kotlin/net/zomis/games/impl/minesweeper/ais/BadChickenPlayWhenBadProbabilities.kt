package net.zomis.games.impl.minesweeper.ais

import net.zomis.games.impl.minesweeper.Flags
import net.zomis.games.impl.minesweeper.MfeProbabilityProvider
import net.zomis.games.impl.minesweeper.WeaponUse
import net.zomis.games.scorers.Scorer
import net.zomis.games.scorers.ScorerFactory
import net.zomis.games.scorers.ScorerScope
import net.zomis.minesweeper.analyze.AnalyzeResult
import net.zomis.minesweeper.analyze.FieldGroup

object BadChickenPlayWhenBadProbabilities {
    fun scorer(factory: ScorerFactory<Flags.Model>, provider: MfeProbabilityProvider): Scorer<Flags.Model, Any> {
        return factory.action(Flags.useWeapon) {
            if (workWithWeapon(this, provider)) scoreFor(RandomAttackScorer::getScoreFor) else 0.0
        }
    }

    fun workWithWeapon(scores: ScorerScope<Flags.Model, WeaponUse>, provider: MfeProbabilityProvider): Boolean {
        return if (!scores.weaponIsClick()) false else isBadProbabilities(
            scores.require(provider)!!
        )
    }

    fun getScoreFor(
        field: Flags.Field,
        analyze: AnalyzeResult<Flags.Field>
    ): Double {
        val grp: FieldGroup<Flags.Field> = analyze.getGroupFor(field)!!
        if (grp.probability < 0.000001) {
            for (ff in field.inverseNeighbors) {
                if (ff.isDiscoveredMine()) return 1.0
            }
        }
        return 0.0
    }

    fun isBadProbabilities(analyze: AnalyzeResult<Flags.Field>): Boolean {
        var highestProb = 0.0
        var prob = 0.0
        for (ee in analyze.groups) {
            if (ZomisTools.isZomisOpenField(ee.fields.get(0))) {
                prob = ee.probability
            }
            if (ee.probability > highestProb) highestProb = ee.probability
        }
        return highestProb <= prob
    }
}