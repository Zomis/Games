package net.zomis.games.ecs

import net.zomis.games.dsl.*
import net.zomis.games.dsl.flow.GameFlowActionScope

interface ECSActionScope<A: Any>: GameFlowActionScope<ECSEntity, A> {
    val root: ECSEntity
    val entity: ECSEntity // Entity that has the action
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
class ECSActionRule<A: Any>(val action: ActionType<ECSEntity, A>, val rule: ECSActionScope<A>.() -> Unit) : ECSRule() {
    fun isRuleForAction(entity: ECSEntity, entityAction: ActionType<ECSEntity, Any>): Boolean {
        if (entityAction.parameterType != action.parameterType) return false
        if (entityAction.serializedType != action.serializedType) return false
        return entityAction.name.substringAfterLast('/') == action.name
    }
}

class ECSActionContextAdapter<A: Any>(
    override val entity: ECSEntity,
    private val action: ActionType<ECSEntity, A>,
    val scope: GameFlowActionScope<ECSEntity, A>
) : ECSActionScope<A>, GameFlowActionScope<ECSEntity, A> by scope {
    override val root: ECSEntity get() = entity.root
}
