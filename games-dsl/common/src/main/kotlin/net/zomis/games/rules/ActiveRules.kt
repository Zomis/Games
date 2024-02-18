package net.zomis.games.rules

import net.zomis.games.dsl.flow.GameMetaScope
import net.zomis.games.dsl.flow.GameModifierImpl
import net.zomis.games.dsl.flow.GameModifierScope

typealias RuleSpec<GameModel, Owner> = GameModifierScope<GameModel, Owner>.() -> Unit
typealias Rule<GameModel, Owner> = GameModifierImpl<GameModel, Owner>

class ActiveRules<GameModel : Any>(private val metaScope: GameMetaScope<GameModel>) {

    private val activeRules = mutableListOf<Rule<GameModel, out Any?>>()
    private val nextRules = mutableListOf<Rule<GameModel, out Any?>>()

    fun fireRules(baseRule: Rule<GameModel, out Any>?) {
        baseRule?.fire()
        // check applicable rules
        // add actions, event listeners
        // state checks

        switchRules()

        // check applicable rules
        // add actions, event listeners
        // state checks

        // Await action
    }

    private fun switchRules() {
        val switchToRules = nextRules.toList()
        nextRules.clear()
        activeRules.clear()
        activeRules.addAll(switchToRules)
    }

}