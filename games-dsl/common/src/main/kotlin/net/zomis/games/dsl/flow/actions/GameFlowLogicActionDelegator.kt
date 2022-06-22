package net.zomis.games.dsl.flow.actions

import net.zomis.games.dsl.Action
import net.zomis.games.dsl.ActionType
import net.zomis.games.dsl.Actionable
import net.zomis.games.dsl.flow.GameFlowActionScope
import net.zomis.games.dsl.flow.GameFlowContext
import net.zomis.games.dsl.impl.*

class GameFlowLogicActionDelegator<T: Any, A: Any>(
    private val gameData: GameRuleContext<T>,
    override val actionType: ActionType<T, A>,
    private val feedback: (FlowStep) -> Unit,
    private val actionTypeImplEntry: () -> ActionTypeImplEntry<T, A>,
    actionDsls: () -> List<GameFlowActionScope<T, A>.() -> Unit>
): GameLogicActionType<T, A> {
    // Keep a list of all ActionDsls here for this specific actionType
    // When asked about something, create one of the following:
    // - GameFlowLogicActionAvailable -- handles availableActions, actionAllowed, actionInfoKeys
    // - GameFlowLogicActionPerform -- handles replayAction and performAction

    private val available = GameFlowLogicActionAvailable(gameData, actionType, actionDsls)
    private val performer = GameFlowLogicActionPerform(gameData, actionDsls, feedback)

    override fun availableActions(playerIndex: Int, sampleSize: ActionSampleSize?): Iterable<Actionable<T, A>>
        = available.availableActions(playerIndex, sampleSize).asSequence()
            .map { createAction(playerIndex, it) }
            .filter { this.actionAllowed(it) }
            .asIterable()

    override fun actionAllowed(action: Actionable<T, A>): Boolean = available.actionAllowed(action)

    override fun replayAction(action: Actionable<T, A>, state: Map<String, Any>?)
        = performer.replay(action, state)

    override fun performAction(action: Actionable<T, A>): FlowStep.ActionResult {
        if (!checkActionAllowed(action)) return FlowStep.IllegalAction(action.actionType, action.playerIndex, action.parameter)
        performer.perform(action)
        return FlowStep.ActionPerformed(action as Actionable<T, Any>,
            actionTypeImplEntry.invoke() as ActionTypeImplEntry<T, Any>, gameData.replayable.stateKeeper.lastMoveState())
    }

    override fun createAction(playerIndex: Int, parameter: A): Actionable<T, A>
        = Action(gameData.game, playerIndex, actionType.name, parameter)

    override fun actionInfoKeys(playerIndex: Int, previouslySelected: List<Any>): List<ActionInfoKey>
        = available.actionInfoKeys(playerIndex, previouslySelected)

    private fun checkActionAllowed(action: Actionable<T, A>): Boolean {
        val allowed = actionAllowed(action)
        if (!allowed) feedback.invoke(FlowStep.IllegalAction(action.actionType, action.playerIndex, action.parameter))
        return allowed
    }

    override fun withChosen(playerIndex: Int, chosen: List<Any>): ActionComplexChosenStep<T, A> {
        return available.withChosen(playerIndex, chosen)
    }

    override fun isComplex(): Boolean = available.isComplex()

}
