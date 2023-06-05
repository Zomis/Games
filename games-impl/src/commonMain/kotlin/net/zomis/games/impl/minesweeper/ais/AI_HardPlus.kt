package net.zomis.minesweeper.ais

import net.zomis.games.dsl.GameDslScope
import net.zomis.games.impl.minesweeper.Flags
import net.zomis.games.impl.minesweeper.MfeAdvancedProbabilityProvider
import net.zomis.games.impl.minesweeper.MfeProbabilityProvider
import net.zomis.games.impl.minesweeper.Weapons
import net.zomis.games.impl.minesweeper.ais.BombScore
import net.zomis.games.impl.minesweeper.ais.OpenFieldProbability
import net.zomis.games.impl.minesweeper.ais.BombIfManyMinesScorer

object AI_HardPlus {
    fun ai(game: GameDslScope<Flags.Model>, analysis: MfeProbabilityProvider, advancedAnalysis: MfeAdvancedProbabilityProvider) {

        // TODO: Play with draw proposal
        val mineProbability = game.scorers.action(Flags.useWeapon) {
            if (action.parameter.weapon !is Weapons.Default) return@action 0.0
            this.require(analysis)!!.getGroupFor(action.game.fieldAt(action.parameter.position))?.probability
        }
        val openFieldProbability = OpenFieldProbability.scorer(game.scorers, advancedAnalysis)
        val bombToWinScorer = BombScore.scorer(game.scorers, analysis)
        val bombIfManyMinesScorer = BombIfManyMinesScorer.scorer(8.0 / 51.0, game.scorers, analysis)

        game.scorers.ai(
            Flags.AI.HardPlus.publicName,
            mineProbability,
            openFieldProbability.weight(0.0001),
            bombToWinScorer,
            bombIfManyMinesScorer,
        )
    }
}