package net.zomis.games.dsl.impl

import net.zomis.games.dsl.*
import kotlin.reflect.KClass

// TODO: Can we reduce generics here? get rid of the `A : Actionable`? Only `GameLogicActionType2D` is special.
interface GameLogicActionType<T : Any, P : Any, A : Actionable<T, P>> {
    val actionType: String
    fun availableActions(playerIndex: Int): Iterable<A>
    fun actionAllowed(action: A): Boolean
    fun replayAction(action: A, state: Map<String, Any>?)
    fun performAction(action: A)
    fun createAction(playerIndex: Int, parameter: P): A
}

data class ActionInfo(val nextOptions: List<Any>, val parameters: List<Any>)
class ActionTypeImplEntry<T : Any, P : Any, A : Actionable<T, P>>(private val model: T,
        private val replayState: ReplayState,
        val actionType: ActionType<P>,
        private val impl: GameLogicActionType<T, P, A>) {
    fun availableActions(playerIndex: Int): Iterable<Actionable<T, P>> = impl.availableActions(playerIndex)
    fun perform(playerIndex: Int, parameter: P) {
        this.perform(this.createAction(playerIndex, parameter))
    }
    fun replayAction(action: A, state: Map<String, Any>?) {
        impl.replayAction(action, state)
    }
    fun perform(action: A) {
        replayState.stateKeeper.clear()
        impl.performAction(action)
    }
    fun createAction(playerIndex: Int, parameter: P): A = impl.createAction(playerIndex, parameter)
    fun isAllowed(action: A): Boolean = impl.actionAllowed(action)
    fun availableParameters(playerIndex: Int, previouslySelected: List<Any>): ActionInfo {
        val serializer: (P) -> Any = { actionType.serialize(it) }
        return if (impl is GameActionRuleContext) {
            impl.actionInfo(playerIndex, previouslySelected, serializer)
        } else {
            if (previouslySelected.isNotEmpty()) {
                throw IllegalArgumentException("Unable to select any options for action ${actionType.name}")
            }
            ActionInfo(emptyList(), availableActions(playerIndex).map { it.parameter }.map(serializer))
        }
    }

    fun createActionFromSerialized(playerIndex: Int, serialized: Any): A {
        val actionOptionsContext = actionOptionsContext(playerIndex)
        if (actionType.parameterType == actionType.serializedType) {
            return createAction(actionOptionsContext.playerIndex, serialized as P)
        }

        val parameter = actionType.deserialize(actionOptionsContext, serialized)
        return if (parameter == null) {
            availableActions(actionOptionsContext.playerIndex).single { action2 ->
                actionType.serialize(action2.parameter) == serialized
            } as A
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

    fun types(): Set<ActionTypeImplEntry<T, Any, Actionable<T, Any>>> {
        return actionTypes.map { type(it)!! }.toSet()
    }

    operator fun get(actionType: String): ActionTypeImplEntry<T, Any, Actionable<T, Any>>? {
        return type(actionType)
    }

    fun type(actionType: String): ActionTypeImplEntry<T, Any, Actionable<T, Any>>? = rules.actionType(actionType)
    fun <P : Any> type(actionType: String, clazz: KClass<T>): ActionTypeImplEntry<T, P, out Actionable<T, P>>? {
        val entry = this.type(actionType)
        if (entry != null) {
            if (entry.actionType.parameterType != clazz) {
                throw IllegalArgumentException("ActionType '$actionType' has parameter ${entry.actionType.parameterType} and not $clazz")
            }
            return this.type(actionType) as ActionTypeImplEntry<T, P, Actionable<T, P>>
        }
        return null
    }

}