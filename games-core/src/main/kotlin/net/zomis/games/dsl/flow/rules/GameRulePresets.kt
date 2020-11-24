package net.zomis.games.dsl.flow.rules

import net.zomis.games.WinResult
import net.zomis.games.dsl.flow.GameFlowRulesContext
import net.zomis.games.dsl.rulebased.GameRuleScope

interface GameRulePresets<T: Any> {
    val players: Players<T>

    interface Players<T: Any> {
        fun singleWinner(winner: GameRuleScope<T>.() -> Int?)
        fun lastPlayerStanding()
        fun losingPlayers(playerIndices: GameRuleScope<T>.() -> Iterable<Int>)
    }
}

class GameRulePresetsImpl<T: Any>(private val context: GameFlowRulesContext<T>): GameRulePresets<T>, GameRulePresets.Players<T> {
    override val players: GameRulePresets.Players<T> = this

    override fun singleWinner(winner: GameRuleScope<T>.() -> Int?) {
        context.rule("declare winner") {
            appliesWhen { winner(this) != null }
            effect { eliminations.singleWinner(winner(this)!!) }
        }
    }

    override fun lastPlayerStanding() {
        context.rule("last player standing wins") {
            appliesWhen { eliminations.remainingPlayers().size == 1 }
            effect { eliminations.eliminateRemaining(WinResult.WIN) }
        }
    }

    override fun losingPlayers(playerIndices: GameRuleScope<T>.() -> Iterable<Int>) {
        context.rule("eliminate losing players") {
            appliesWhen { playerIndices().filter { eliminations.isAlive(it) }.any() }
            effect {
                val eliminating = playerIndices().filter { eliminations.isAlive(it) }
                println("Eliminate losing players: $eliminating")
                eliminations.eliminateMany(eliminating, WinResult.LOSS)
            }
        }
    }

}
