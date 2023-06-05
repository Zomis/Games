package net.zomis.games.impl.minesweeper.ais

import net.zomis.games.impl.minesweeper.Flags
import net.zomis.minesweeper.analyze.AnalyzeResult

object MineprobHelper {
    const val THRESHOLD = 0.9999

    fun find100(require: AnalyzeResult<Flags.Field>): Int {
        return require.groups.filter { it.probability > THRESHOLD }.sumOf { it.size }
    }

}