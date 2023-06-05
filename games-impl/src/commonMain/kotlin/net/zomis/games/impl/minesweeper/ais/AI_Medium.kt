package net.zomis.games.impl.minesweeper.ais

import net.zomis.games.dsl.GameDslScope
import net.zomis.games.impl.minesweeper.Flags
import net.zomis.games.impl.minesweeper.SizedBombWeapon

object AI_Medium {
    fun ai(game: GameDslScope<Flags.Model>) {

        val neighborsNeedsMoreMines = NeighborsNeedsMoreMines.scorer(game.scorers)
        val randomIdiotScorer = RandomIdiotScorer.scorer(game.scorers)
        val easy100Scorer = Easy100Scorer.scorer(game.scorers)
        val randomBomber = RandomBomber.scorer(game.scorers)
        val staticScoreForWeapon = StaticScoreForWeapon.scorer(game.scorers) { it is SizedBombWeapon }

        game.scorers.ai(
            Flags.AI.Medium.publicName,
            neighborsNeedsMoreMines.weight(0.3),
            randomIdiotScorer,
            easy100Scorer.weight(10000),
            randomBomber.weight(100),
            staticScoreForWeapon.weight(-100)
        )

    }
}