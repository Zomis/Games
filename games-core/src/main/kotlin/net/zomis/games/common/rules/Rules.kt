package net.zomis.games.common.rules

import net.zomis.games.WinResult
import net.zomis.games.dsl.flow.GameFlowRules
import net.zomis.games.dsl.rulebased.GameCommonRule
import net.zomis.games.dsl.rulebased.GameRules

class GameDslRuleCreator<T: Any>(private val dsl: GameRules<T>): RuleCreator<T> {
    override fun rule(name: String, rule: GameCommonRule<T>.() -> Unit) {
        dsl.rule(name, rule)
    }
}

class GameFlowRuleCreator<T: Any>(private val dsl: GameFlowRules<T>): RuleCreator<T> {
    override fun rule(name: String, rule: GameCommonRule<T>.() -> Unit) {
        dsl.rule(name, rule)
    }
}

interface RuleCreator<T: Any> {
    fun rule(name: String, rule: GameCommonRule<T>.() -> Unit)
}

class PlayerRules<T: Any>(private val ruleCreator: RuleCreator<T>) {
    fun lastPlayerStanding() {
        ruleCreator.rule("last player standing") {
            appliesWhen { eliminations.remainingPlayers().size == 1 }
            effect { eliminations.eliminateRemaining(WinResult.WIN) }
        }
    }
}

class Rules<T: Any>(private val ruleCreator: RuleCreator<T>) {
    val players get() = PlayerRules(ruleCreator)
}

val <T: Any> GameRules<T>.rules get() = Rules(GameDslRuleCreator(this))
