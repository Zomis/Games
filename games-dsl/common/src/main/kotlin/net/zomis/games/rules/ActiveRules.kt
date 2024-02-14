package net.zomis.games.rules

import net.zomis.games.dsl.flow.GameMetaScope
import net.zomis.games.dsl.flow.GameModifierScope

typealias RuleSpec<GameModel, Owner> = GameModifierScope<GameModel, Owner>.() -> Unit
class Rule<GameModel : Any, Owner>(val owner: Owner, val spec: RuleSpec<GameModel, Owner>) {
    // states
    fun disable() { TODO() }
    fun isActive(): Boolean = TODO()


}

class ActiveRules<GameModel : Any>(private val metaScope: GameMetaScope<GameModel>) {

    private val activeRules = mutableListOf<Rule<GameModel, out Any?>>()
    private val nextRules = mutableListOf<Rule<GameModel, out Any?>>()

    fun fireRules() {

    }

}