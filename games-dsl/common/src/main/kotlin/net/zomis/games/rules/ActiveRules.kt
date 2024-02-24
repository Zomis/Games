package net.zomis.games.rules

import net.zomis.games.dsl.flow.GameMetaScope

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