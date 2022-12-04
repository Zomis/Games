package net.zomis.games.dsl.impl

import net.zomis.games.dsl.ActionPlayerChoice
import net.zomis.games.dsl.ActionType
import net.zomis.games.dsl.ActionView
import net.zomis.games.dsl.ActionsChosenView
import net.zomis.games.dsl.ActionsView
import kotlin.reflect.KClass

class ActionsViewImpl<T: Any>(
    private val game: Game<T>,
    private val viewer: PlayerViewer,
    private val useChosen: Boolean
) : ActionsView<T>, ActionsChosenView<T> {
    override fun chosen(): ActionPlayerChoice? {
        if (viewer.playerIndex == null) return null
        return if (useChosen) game.actions.choices.getChosen(viewer.playerIndex) else null
    }

    override fun <E : Any> nextSteps(clazz: KClass<E>): List<E> {
        if (viewer.playerIndex == null) return emptyList()
        val chosen = chosen() ?: return emptyList()
        val actionType = game.actions[chosen.actionType] ?: return emptyList()
        val next = actionType.withChosen(viewer.playerIndex, chosen.chosen).nextOptions()
        return next.filter { clazz.isInstance(it.choiceValue) }.map { it.choiceValue }.toList() as List<E>
    }
}

class ActionViewImpl<T: Any, A: Any>(
    private val game: Game<T>,
    private val actionType: ActionType<T, A>,
    private val viewer: PlayerViewer,
    private val chosen: List<Any> = emptyList()
) : ActionView<T, A> {
    private val actionEntry = game.actions.type(actionType)
    private val playerIndex: Int? = viewer.playerIndex

    override fun anyAvailable(): Boolean {
        if (playerIndex == null) return false
        if (chosen.isEmpty()) {
            val availableActions = actionEntry?.availableActions(playerIndex, null) ?: emptyList()
            return availableActions.any { actionEntry?.isAllowed(it) ?: false }
        }
        return actionEntry?.withChosen(playerIndex, chosen)
            ?.depthFirstActions(null)
            ?.map { actionEntry.createAction(playerIndex, it.parameter) }
            ?.filter { actionEntry.isAllowed(it) }?.any() ?: false
    }

    private fun next(): Sequence<ActionNextChoice<T, A>> {
        if (playerIndex == null) return emptySequence()
        if (actionEntry == null) return emptySequence()

        return game.actions.type(actionType)?.withChosen(playerIndex, chosen)?.nextOptions() ?: emptySequence()
    }

    override fun <E : Any> nextSteps(clazz: KClass<E>): List<E> {
        return next().filter { clazz.isInstance(it.choiceValue) }.map { it.choiceValue }.toList() as List<E>
    }

    override fun nextStepsAll(): Map<Any, Any> {
        return next().toList().associate { it.choiceKey to it.choiceValue }
    }

    override fun choose(next: Any): ActionView<T, A> {
        return ActionViewImpl(game, actionType, viewer, chosen + next)
    }

    override fun options(): List<A> {
        if (playerIndex == null) return emptyList()
        if (this.chosen.isEmpty()) {
            return actionEntry?.availableActions(playerIndex, null)?.map { it.parameter } ?: emptyList()
        }
        val next = actionEntry?.withChosen(playerIndex, chosen)
            ?.depthFirstActions(null)
        return next?.map { it.parameter }?.toList() ?: emptyList()
    }

}
