package net.zomis.games.ecsmodel

interface EcsRuleScope {
    fun applyRules(rules: List<Rule>)
}

class Rule {
    // Remember: copyable, viewable, customizable
}
