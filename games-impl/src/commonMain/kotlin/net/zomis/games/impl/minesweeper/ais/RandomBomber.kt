package net.zomis.games.impl.minesweeper.ais

import net.zomis.games.dsl.Actionable
import net.zomis.games.impl.minesweeper.Flags
import net.zomis.games.impl.minesweeper.Weapon
import net.zomis.games.impl.minesweeper.WeaponUse
import net.zomis.games.scorers.ScorerFactory
import net.zomis.games.scorers.ScorerScope
import kotlin.random.Random

object RandomBomber {
    fun workWithWeapon(scores: ScorerScope<Flags.Model, WeaponUse>, require: Boolean?, require2: Int?): Boolean {
        return scores.weaponIsBomb() && ereDags(scores.action, require!!, require2!!)
    }

    fun getScoreFor(
        map: Flags.Model,
        weapon: Weapon,
        player: Int,
        field: Flags.Field,
    ): Double {
        return unclicked(weapon.affectedArea(map, player, field.point).map { map.fieldAt(it) })
    }

    private fun unclicked(fieldsAffected: Collection<Flags.Field>): Double {
        var i = 0
        for (ff in fieldsAffected) {
            if (!ff.clicked) i++
        }
        return i.toDouble()
    }

    /**
     * @author Tejpbit
     */
    private fun ereDags(action: Actionable<Flags.Model, WeaponUse>, require: Boolean, random2: Int): Boolean {
        // Code by Tejpbit
        if (require) {
            val avarage = action.game.players.sumOf { it.score } / action.game.players.size.toDouble()
            if (action.game.players[action.game.currentPlayer].score < avarage - (random2 + 2)) {
                return true
            }
            if (getHighestScore(action.game) >= action.game.totalMines() * 0.4) {
                return true
            }
        }
        return false
    }

    /**
     * @author Tejpbit
     */
    private fun getHighestScore(map: Flags.Model): Int = map.players.maxOf { it.score }

    fun scorer(scorers: ScorerFactory<Flags.Model>) = run {
        val random1 = scorers.provider { Random.Default.nextInt(100) < 42 }
        val random2 = scorers.provider { Random.Default.nextInt(3) }

        scorers.action(Flags.useWeapon) {
            if (!workWithWeapon(this, require(random1), require(random2))) return@action 0.0
            getScoreFor(action.game, action.parameter.weapon, action.playerIndex, action.game.fieldAt(action.parameter.position))
        }
    }
}