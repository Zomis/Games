package net.zomis.games.impl.minesweeper.ais

import net.zomis.games.dsl.GameDslScope
import net.zomis.games.impl.minesweeper.Flags
import net.zomis.games.impl.minesweeper.Weapons
import net.zomis.games.scorers.Scorer
import net.zomis.games.scorers.ScorerAnalyzeProvider
import net.zomis.minesweeper.analyze.AnalyzeResult

object HighestMineProbability {

    fun scorer(
        game: GameDslScope<Flags.Model>,
        analysis: ScorerAnalyzeProvider<Flags.Model, AnalyzeResult<Flags.Field>>
    ): Scorer<Flags.Model, Any> {

        val highestMineProb = game.scorers.provider { sc ->
            val analyseResult = sc.require(analysis)
            analyseResult!!.groups.maxOf { it.probability }
        }

        return game.scorers.action(Flags.useWeapon) {
            val analyseResult = require(analysis)!!

            if (action.parameter.weapon !is Weapons.Default) return@action 0.0
            if (BadChickenPlayWhenBadProbabilities.isBadProbabilities(analyseResult)) return@action 0.0

            val highestMineProbResult = require(highestMineProb)!!
            val field = action.game.fieldAt(action.parameter.position)
            val fieldGroup = analyseResult.getGroupFor(field) ?: return@action 0.0
            if (fieldGroup.probability == highestMineProbResult) 1.0 else 0.0
        }
    }

}
/*
*
    private var highestProb = 0.0
    fun workWithWeapon(scores: ScoreParameters): Boolean {
        if (!this.weaponIsClick(scores.getWeapon())) return false
        highestProb = 0.0
        for (ee in scores.getAnalyze().getAnalyze().getGroups()) {
            if (ee.probability > highestProb) highestProb = ee.probability
        }
        return !BadChickenPlayWhenBadProbabilities.isBadProbabilities(scores.getAnalyze().getAnalyze())
    }

    fun getScoreFor(
        field: MinesweeperField?,
        data: ProbabilityKnowledge<MinesweeperField?>?,
        scores: ScoreParameters
    ): Double {
        val grp: FieldGroup<MinesweeperField> = scores.getAnalyze().getAnalyze().getGroupFor(field) ?: return 0
        return if (grp.probability == highestProb) 1 else 0
    }
*/