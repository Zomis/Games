package net.zomis.games.dsl.impl

import net.zomis.games.dsl.*
import kotlin.reflect.KClass

// TODO: Can we reduce generics here? get rid of the `A : Actionable`? Only `GameLogicActionType2D` is special.
interface GameLogicActionType<T : Any, P : Any> {
    val actionType: String
    fun availableActions(playerIndex: Int): Iterable<Actionable<T, P>>
    fun actionAllowed(action: Actionable<T, P>): Boolean
    fun replayAction(action: Actionable<T, P>, state: Map<String, Any>?)
    fun performAction(action: Actionable<T, P>)
    fun createAction(playerIndex: Int, parameter: P): Actionable<T, P>
}

data class ActionInfo(val nextOptions: List<Pair<String?, Any>>, val parameters: List<Any>)
class ActionTypeImplEntry<T : Any, P : Any>(private val model: T,
        private val replayState: ReplayState,
        val actionType: ActionType<P>,
        private val impl: GameLogicActionType<T, P>) {
    fun availableActions(playerIndex: Int): Iterable<Actionable<T, P>> = impl.availableActions(playerIndex)
    fun perform(playerIndex: Int, parameter: P) {
        this.perform(this.createAction(playerIndex, parameter))
    }
    fun replayAction(action: Actionable<T, P>, state: Map<String, Any>?) {
        impl.replayAction(action, state)
    }
    fun perform(action: Actionable<T, P>) {
        replayState.stateKeeper.clear()
        impl.performAction(action)
    }
    fun createAction(playerIndex: Int, parameter: P): Actionable<T, P> = impl.createAction(playerIndex, parameter)
    fun isAllowed(action: Actionable<T, P>): Boolean = impl.actionAllowed(action)
    fun availableParameters(playerIndex: Int, previouslySelected: List<Any>): ActionInfo {
        val serializer: (P) -> Any = { actionType.serialize(it) }
        return if (impl is GameActionRuleContext) {
            impl.actionInfo(playerIndex, previouslySelected, serializer)
        } else {
            if (previouslySelected.isNotEmpty()) {
                throw IllegalArgumentException("Unable to select any options for action ${actionType.name}")
            }
            ActionInfo(emptyList(), availableActions(playerIndex).map { it.parameter })
        }
    }

    fun createActionFromSerialized(playerIndex: Int, serialized: Any): Actionable<T, P> {
        val actionOptionsContext = actionOptionsContext(playerIndex)
        if (actionType.parameterType == actionType.serializedType) {
            return createAction(actionOptionsContext.playerIndex, serialized as P)
        }

        val parameter = actionType.deserialize(actionOptionsContext, serialized)
        return if (parameter == null) {
            val actions = availableActions(actionOptionsContext.playerIndex).filter { action2 ->
                actionType.serialize(action2.parameter) == serialized
            }
            if (actions.size != 1) {
                throw IllegalStateException("Actions available: ${actions.size} for player $playerIndex move $serialized")
            }
            actions.single()
        } else {
            createAction(actionOptionsContext.playerIndex, parameter)
        }
    }

    fun actionOptionsContext(playerIndex: Int): ActionOptionsContext<T> {
        return ActionOptionsContext(model, this.actionType.name, playerIndex)
    }

    val name: String
        get() = actionType.name
    val parameterClass: KClass<P>
        get() = actionType.parameterType
}

class ActionsImpl<T : Any>(
    private val model: T,
    private val rules: GameRulesContext<T>,
    private val replayState: ReplayState
) {

    val actionTypes: Set<String> get() = rules.actionTypes()

    fun types(): Set<ActionTypeImplEntry<T, Any>> {
        return actionTypes.map { type(it)!! }.toSet()
    }

    operator fun get(actionType: String): ActionTypeImplEntry<T, Any>? {
        return type(actionType)
    }

    fun type(actionType: String): ActionTypeImplEntry<T, Any>? = rules.actionType(actionType)
    fun <P : Any> type(actionType: String, clazz: KClass<P>): ActionTypeImplEntry<T, P>? {
        val entry = this.type(actionType)
        if (entry != null) {
            if (entry.actionType.parameterType != clazz) {
                throw IllegalArgumentException("ActionType '$actionType' has parameter ${entry.actionType.parameterType} and not $clazz")
            }
            return this.type(actionType) as ActionTypeImplEntry<T, P>
        }
        return null
    }

}