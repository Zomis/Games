package net.zomis.games.impl.minesweeper.ais

import net.zomis.games.impl.minesweeper.Flags
import net.zomis.minesweeper.analyze.detail.ProbabilityKnowledge

object ZomisTools {
    fun playerHasMostScore(game: Flags.Model, playerIndex: Int): Boolean {
        val playerScore = game.players[playerIndex].score
        return game.players.none { it.score > playerScore }
    }

    fun isAIChallengerField(fieldData: ProbabilityKnowledge<Flags.Field>): Boolean {
        return !isZomisOpenField(fieldData.field) && fieldData.mineProbability > 0
    }

    fun fieldFoundMines(field: Flags.Field): Int {
        var i = 0
        for (ff in field.neighbors) {
            if (ff.isDiscoveredMine()) i++
        }
        return i
    }

    fun fieldNeedsMoreMines(field: Flags.Field): Int {
        if (!field.clicked) return 0
        return if (field.isDiscoveredMine()) 0 else field.knownValue - fieldFoundMines(
            field
        )
    }

    fun isZomisOpenField(field: Flags.Field): Boolean {
        // If field has a neighbor that is clicked, it is not in open field group.
        for (ff in field.neighbors) {
            if (ff.clicked && !ff.isDiscoveredMine()) return false
        }
        return true
        //		return this.analyze.getKnowledgeFor(field).getFieldGroup().size() > 8;
    }
}