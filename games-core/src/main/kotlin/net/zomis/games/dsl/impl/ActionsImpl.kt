package net.zomis.games.dsl.impl

import net.zomis.games.common.mergeWith
import net.zomis.games.dsl.*
import kotlin.reflect.KClass

interface GameLogicActionType<T : Any, P : Any> {
    val actionType: ActionType<T, P>
    fun availableActions(playerIndex: Int, sampleSize: ActionSampleSize?): Iterable<Actionable<T, P>>
    fun actionAllowed(action: Actionable<T, P>): Boolean
    fun replayAction(action: Actionable<T, P>, state: Map<String, Any>?)
    fun performAction(action: Actionable<T, P>)
    fun createAction(playerIndex: Int, parameter: P): Actionable<T, P>
}

data class ActionInfo<T: Any, A: Any>(val actionType: ActionType<T, A>, val parameter: A?, val nextStep: Any?)
data class ActionInfoKey(val serialized: Any, val actionType: String, val highlightKeys: List<Any>, val isParameter: Boolean)
data class ActionInfoByKey(val keys: Map<Any, List<ActionInfoKey>>) {
    operator fun plus(other: ActionInfoByKey): ActionInfoByKey {
        return ActionInfoByKey(keys.toMutableMap().mergeWith(other.keys) { a, b -> (a ?: emptyList()) + (b ?: emptyList()) })
    }
}
data class ActionSampleSize(val sampleSizes: List<Int>) {
    fun nextSample(): Pair<Int, ActionSampleSize> = sampleSizes.first() to ActionSampleSize(sampleSizes.subList(1, sampleSizes.size))
}

class ActionTypeImplEntry<T : Any, P : Any>(private val model: T,
    private val replayState: ReplayState,
    val actionType: ActionType<T, P>,
    private val impl: GameLogicActionType<T, P>
) {
    fun availableActions(playerIndex: Int, sampleSize: ActionSampleSize?): Iterable<Actionable<T, P>> = impl.availableActions(playerIndex, sampleSize)
    fun perform(playerIndex: Int, parameter: P) = this.perform(this.createAction(playerIndex, parameter))
    fun replayAction(action: Actionable<T, P>, state: Map<String, Any>?) {
        impl.replayAction(action, state)
    }
    fun perform(action: Actionable<T, P>) {
        replayState.stateKeeper.clear()
        impl.performAction(action)
    }
    fun createAction(playerIndex: Int, parameter: P): Actionable<T, P> = impl.createAction(playerIndex, parameter)
    fun isAllowed(action: Actionable<T, P>): Boolean = impl.actionAllowed(action)

    fun createActionFromSerialized(playerIndex: Int, serialized: Any): Actionable<T, P> {
        val actionOptionsContext = actionOptionsContext(playerIndex)
        if (actionType.parameterType == actionType.serializedType) {
            return createAction(actionOptionsContext.playerIndex, serialized as P)
        }

        val parameter = actionType.deserialize(actionOptionsContext, serialized)
        return if (parameter == null) {
            val actions = availableActions(actionOptionsContext.playerIndex, null).filter { action2 ->
                actionType.serialize(action2.parameter) == serialized
            }.distinct()
            if (actions.size != 1) {
                throw IllegalStateException("Actions available: ${actions.size} for player $playerIndex move $serialized")
            }
            actions.single()
        } else {
            createAction(actionOptionsContext.playerIndex, parameter)
        }
    }

    fun actionOptionsContext(playerIndex: Int): ActionOptionsContext<T>
        = ActionOptionsContext(model, this.actionType.name, playerIndex)

    fun actionInfoKeys(playerIndex: Int, previouslySelected: List<Any>): ActionInfoByKey {
        val ruleContext = impl as GameActionRuleContext<T, P>?
            ?: throw UnsupportedOperationException("Impl class ${impl::class} not supported for actionType ${actionType.name}")
        return ActionInfoByKey(ruleContext.actionInfoKeys(playerIndex, previouslySelected).groupBy { it.serialized })
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

    fun <A: Any> type(actionType: ActionType<T, A>) = rules.actionType(actionType.name) as ActionTypeImplEntry<T, A>
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

    fun allActionInfo(playerIndex: Int, previouslySelected: List<Any>): ActionInfoByKey {
        return types().fold(ActionInfoByKey(emptyMap())) { acc, next ->
            acc + next.actionInfoKeys(playerIndex, previouslySelected)
        }
    }

}