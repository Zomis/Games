package net.zomis.games.dsl.flow.actions

import net.zomis.games.dsl.ActionRuleScope
import net.zomis.games.dsl.Actionable
import net.zomis.games.dsl.flow.GameFlowActionContext
import net.zomis.games.dsl.flow.GameFlowActionDsl
import net.zomis.games.dsl.flow.GameMetaScope
import net.zomis.games.dsl.impl.ActionRuleContext

class GameFlowActionContextPerform<T: Any, A: Any>(val context: ActionRuleContext<T, A>): GameFlowActionContext<T, A>() {
    override fun perform(rule: ActionRuleScope<T, A>.() -> Unit) {
        rule.invoke(context)
    }
}
class GameFlowActionContextAfter<T: Any, A: Any>(val context: ActionRuleContext<T, A>): GameFlowActionContext<T, A>() {
    override fun after(rule: ActionRuleScope<T, A>.() -> Unit) {
        rule.invoke(context)
    }
}

class GameFlowLogicActionPerform<T: Any, A: Any>(
    private val gameData: GameMetaScope<T>,
    private val actionDsls: () -> List<GameFlowActionDsl<T, A>>,
) {
    private fun createContext(action: Actionable<T, A>) = ActionRuleContext(gameData, action)

    fun perform(action: Actionable<T, A>) {
        val context = GameFlowActionContextPerform(createContext(action))
        actionDsls().forEach { it.invoke(context) }
    }

}
