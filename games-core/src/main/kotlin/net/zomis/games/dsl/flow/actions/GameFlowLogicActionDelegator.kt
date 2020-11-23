package net.zomis.games.dsl.flow.actions

import net.zomis.games.dsl.ActionType
import net.zomis.games.dsl.Actionable
import net.zomis.games.dsl.impl.*

class GameFlowLogicActionDelegator<T: Any, A: Any>(
    private val gameData: GameRuleContext<T>,
    override val actionType: ActionType<T, A>
): GameLogicActionType<T, A> {
    // Keep a list of all ActionDsls here for this specific actionType
    // When asked about something, create one of the following:
    // - GameFlowLogicActionAvailable -- handles availableActions, actionAllowed, actionInfoKeys
    // - GameFlowLogicActionPerform -- handles replayAction and performAction

    override fun availableActions(playerIndex: Int, sampleSize: ActionSampleSize?): Iterable<Actionable<T, A>> {
        TODO("Not yet implemented")
    }

    override fun actionAllowed(action: Actionable<T, A>): Boolean {
        TODO("Not yet implemented")
    }

    override fun replayAction(action: Actionable<T, A>, state: Map<String, Any>?) {
        TODO("Not yet implemented")
    }

    override fun performAction(action: Actionable<T, A>) {
        TODO("Not yet implemented")
    }

    override fun createAction(playerIndex: Int, parameter: A): Actionable<T, A> {
        TODO("Not yet implemented")
    }

    override fun actionInfoKeys(playerIndex: Int, previouslySelected: List<Any>): List<ActionInfoKey> {
        TODO()
    }

}