package net.zomis.games.impl.minesweeper.ais

import net.zomis.games.components.Point
import net.zomis.games.impl.minesweeper.Flags
import net.zomis.games.impl.minesweeper.WeaponUse
import net.zomis.minesweeper.analyze.*

class BombTools(val model: Flags.Model, val player: Flags.Player) {

    fun checkBomb(analyzeResult: AnalyzeResult<Flags.Field>): WeaponUse? {
        var highest = 0.0
        var highestField: Flags.Field? = null
        for (ff in model.grid.all()) {
            val bombprob = calcBombProb(model, ff.value, analyzeResult)
            if (bombprob > highest) {
                highest = bombprob
                highestField = ff.value
            }
        }
        return if (player.score + highest >= 25.5) {
            player.bombWeaponUse(highestField!!)
        } else null
    }

    fun calcBombProb(game: Flags.Model, ff: Flags.Field, analyzeResult: AnalyzeResult<Flags.Field>): Double {
        var total = 0.0
        for (xx in -2..2) {
            for (yy in -2..2) {
                val relative: Flags.Field = game.getRelativePosition(ff, xx, yy) ?: continue
                if (relative.clicked) continue
                total += analyzeResult.getGroupFor(relative)?.probability ?: 0.0
            }
        }
        return total
    }

    companion object {
        const val NEEDED_SCORE = 26

        fun isInterestingSolution(solution: Solution<Flags.Field>): Boolean {
            for ((key, value) in solution.getSetGroupValues().entrySet()) {
                if (key.size - value != 1) { // check for 2a = 1, 3a = 2, 4a = 3... (commonly EV 0 situations)
                    if (key.size <= 8) return true
                }
            }
            return false
        }

        fun getBombWinPercent(
            model: Flags.Model,
            analyze: AnalyzeResult<Flags.Field>,
            bombLocation: Flags.Field,
            score: Int
        ): Double {
            var remaining: Int = model.remainingMines()
            val needed: Int = NEEDED_SCORE - score
            val adj: List<Flags.Field> = getBombAdjacents(model, bombLocation).filter { !it.clicked }
            if (remaining > adj.size) remaining = adj.size
            var total = 0.0
            for (i in needed..remaining) {
                val list: MutableList<RuleConstraint<Flags.Field>> = mutableListOf()
                val bombRule: FieldRule<Flags.Field> = FieldRule<Flags.Field>(bombLocation, adj, i)
                list.add(bombRule)
                val probI: Double = analyze.getProbabilityOf(list)
                //			logger.info("win% for " + bombLocation + ": " + i + " mines with probability of " + probI);
                total += probI
            }
            return total
        }

        fun getBombAdjacents(model: Flags.Model, field: Flags.Field): List<Flags.Field> {
            var field: Flags.Field = field
            val RANGE = 2
            val xpos: Int = field.x.coerceAtLeast(RANGE).coerceAtMost(model.grid.sizeX - RANGE - 1)
            val ypos: Int = field.y.coerceAtLeast(RANGE).coerceAtMost(model.grid.sizeY - RANGE - 1)
            field = model.fieldAt(Point(xpos, ypos))
            val bombAdjacents: MutableList<Flags.Field> = mutableListOf()
            val x: Int = field.x
            val y: Int = field.y
            for (yy in -2..2) {
                for (xx in -2..2) {
                    if (x + xx >= 0 && x + xx < model.grid.sizeX && y + yy >= 0 && y + yy < model.grid.sizeY)
                        bombAdjacents.add(model.getRelativePosition(field, xx, yy))
                }
            }
            return bombAdjacents
        }

        fun getBestBomb(model: Flags.Model, analyze: AnalyzeResult<Flags.Field>): Flags.Field? {
            var max = 0.0
            var field: Flags.Field? = null
            for (ff in model.grid.all()) {
                val bomb: Double = getBombProbability(getBombAdjacents(model, ff.value), analyze)
                if (bomb > max) {
                    field = ff.value
                    max = bomb
                }
            }
            return field
        }

        fun getBombProbability(model: Flags.Model, bombCenter: Flags.Field, analyze: AnalyzeResult<Flags.Field>): Double {
            return getBombProbability(getBombAdjacents(model, bombCenter), analyze)
        }

        fun getBombProbability(
            affected: Collection<Flags.Field>,
            analyze: AnalyzeResult<Flags.Field>
        ): Double {
            var total = 0.0
            for (field in affected) {
                if (field.clicked) continue
                val know: FieldGroup<Flags.Field> = analyze.getGroupFor(field) ?: continue
                total += know.probability
            }
            return total
        }
    }
}