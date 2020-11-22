package net.zomis.games.dsl.flow.rules

import net.zomis.games.dsl.flow.GameFlowRulesContext
import net.zomis.games.dsl.rulebased.GameRuleScope

interface GameRulePresets<T: Any> {
    val players: Players<T>

    interface Players<T: Any> {
        fun singleWinner(winner: GameRuleScope<T>.() -> Int?)
    }
}

class GameRulePresetsImpl<T: Any>(private val context: GameFlowRulesContext<T>): GameRulePresets<T>, GameRulePresets.Players<T> {
    override val players: GameRulePresets.Players<T> = this

    override fun singleWinner(winner: GameRuleScope<T>.() -> Int?) {
        context.rule("game end") {
            appliesWhen { winner(this) != null }
            effect { eliminations.singleWinner(winner(this)!!) }
        }
    }

}
