package net.zomis.games.dsl.flow.actions

import net.zomis.games.dsl.ActionRuleScope
import net.zomis.games.dsl.Actionable
import net.zomis.games.dsl.flow.GameFlowActionContext
import net.zomis.games.dsl.flow.GameFlowActionDsl
import net.zomis.games.dsl.flow.GameFlowActionScope
import net.zomis.games.dsl.flow.GameFlowContext
import net.zomis.games.dsl.impl.ActionRuleContext
import net.zomis.games.dsl.impl.GameRuleContext

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
    private val gameData: GameRuleContext<T>,
    private val actionDsls: () -> List<GameFlowActionDsl<T, A>>,
    private val feedback: (GameFlowContext.Steps.FlowStep) -> Unit
) {
    private fun createContext(action: Actionable<T, A>)
        = ActionRuleContext(gameData.game, action, gameData.eliminations, gameData.replayable)

    fun replay(action: Actionable<T, A>, state: Map<String, Any>?) {
        if (state != null) {
            gameData.replayable.setReplayState(state)
        }
        this.perform(action)
    }

    fun perform(action: Actionable<T, A>) {
        val context = GameFlowActionContextPerform(createContext(action))
        actionDsls().forEach { it.invoke(context) }
    }

}
