package net.zomis.games.impl.minesweeper.ais

import net.zomis.games.dsl.GameDslScope
import net.zomis.games.impl.minesweeper.Flags
import net.zomis.games.impl.minesweeper.MfeProbabilityProvider
import net.zomis.games.impl.minesweeper.ais.BadChickenPlayWhenBadProbabilities
import net.zomis.games.impl.minesweeper.ais.HighestMineProbability
import net.zomis.games.impl.minesweeper.ais.RandomAttackScorer
import net.zomis.games.impl.minesweeper.ais.BombScore

object AI_Hard {

    fun ai(game: GameDslScope<Flags.Model>, analysis: MfeProbabilityProvider) {
        val highestMineProb = HighestMineProbability.scorer(game, analysis)
        val randomAttackScorer = RandomAttackScorer.scorer(game.scorers, analysis)
        val badChickenPlayWhenBadProbabilities = BadChickenPlayWhenBadProbabilities.scorer(game.scorers, analysis)
        val bombToWinScorer = BombScore.scorer(game.scorers, analysis)

        game.scorers.ai(
            Flags.AI.Hard.publicName,
            highestMineProb,
            randomAttackScorer.weight(0.5),
            badChickenPlayWhenBadProbabilities.weight(0.1),
            bombToWinScorer
        )
    }

}
