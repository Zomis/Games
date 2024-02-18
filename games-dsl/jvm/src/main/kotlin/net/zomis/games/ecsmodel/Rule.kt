package net.zomis.games.ecsmodel

interface EcsRuleScope {
    // Rules may contain: Event listeners, Actions.

    fun applyRules(rules: List<Rule>)
    fun removeRule(rule: Rule)
    fun action(action: EcsAction, actionRules: EcsAction.() -> Unit = {}) {
        TODO()
        // See GameFlowStepScope.yieldAction / enableAction / actionHandler
    }
}

interface EcsAction {

}

class Rule {
    // Remember: copyable, viewable, customizable
}
