package net.zomis.games.dsl.listeners

import kotlinx.coroutines.CoroutineScope
import net.zomis.games.dsl.ActionType
import net.zomis.games.dsl.Actionable
import net.zomis.games.dsl.GameListener
import net.zomis.games.dsl.impl.FlowStep

class RememberMovesListener(val count: Int): GameListener {

    init {
        require(count >= 1)
    }

    private val actions = mutableListOf<Actionable<Any, Any>>()

    override suspend fun handle(coroutineScope: CoroutineScope, step: FlowStep) {
        if (step is FlowStep.ActionPerformed<*>) {
            actions.add(step.action as Actionable<Any, Any>)
            while (actions.size > count) actions.removeFirst()
        }
    }

    fun <T: Any, A: Any> lastMoveAs(actionType: ActionType<T, A>): Actionable<T, A> {
        val last = actions.last()
        check(last.actionType == actionType.name) { "Action types does not match. Found ${last.actionType} but wanted ${actionType.name}" }
        return last as Actionable<T, A>
    }

}