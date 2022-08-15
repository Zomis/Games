package net.zomis.games.ecs

import net.zomis.games.PlayerEliminationsRead
import net.zomis.games.PlayerEliminationsWrite
import net.zomis.games.dsl.ActionType
import net.zomis.games.dsl.GameActionCreator

interface ECSActionRulePreconditionScope {
    val playerIndex: Int
    val eliminations: PlayerEliminationsRead
}
interface ECSActionRuleRequiresScope: ECSActionRulePreconditionScope
interface ECSActionRuleEffectScope: ECSActionRuleRequiresScope {
    override val eliminations: PlayerEliminationsWrite
}

interface ECSActionRuleScope {
    val root: ECSEntity
    val entity: ECSEntity // Entity that has the action

    fun precondition(condition: ECSActionRulePreconditionScope.() -> Boolean) {}
    fun requires(condition: ECSActionRuleRequiresScope.() -> Boolean) {}
    fun effect(condition: ECSActionRuleEffectScope.() -> Unit) {}

}

class ECSActions(val actions: List<ActionType<ECSEntity, out Any>>) {
    fun createActions(entity: ECSEntity): List<ActionType<ECSEntity, out Any>> {
        return actions.map { it as GameActionCreator<ECSEntity, out Any> }.map { it.withName(entity.path() + "/" + it.name) }
    }

    companion object Key: ECSAccessor<ECSActions>("actions")
}
class ECSRules(val rules: List<ECSRule>) {
    companion object Key: ECSAccessor<ECSRules>("rules")
}
open class ECSRule
class ECSActionRule(val action: ActionType<ECSEntity, out Any>, val rule: ECSActionRuleScope.() -> Unit) : ECSRule()
