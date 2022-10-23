package net.zomis.games.dsl.impl

import net.zomis.games.common.mergeWith
import net.zomis.games.dsl.*
import kotlin.reflect.KClass

@GameMarker
interface GameLogicActionTypeChosen<T: Any, P: Any> {
    val actionType: ActionType<T, P>
    val playerIndex: Int
    val chosen: List<Any>
    fun nextOptions(): Sequence<ActionNextChoice<T, P>>
    fun parameters(): Sequence<ActionNextParameter<T, P>>
    fun depthFirstActions(sampling: ActionSampleSize?): Sequence<ActionNextParameter<T, P>>
    fun actionKeys(): List<ActionInfoKey>
}
interface ActionComplexChosenStep<T: Any, P: Any> : GameLogicActionTypeChosen<T, P>

interface GameLogicActionType<T : Any, P : Any> {
    val actionType: ActionType<T, P>
    fun isComplex(): Boolean
    fun allActions(playerIndex: Int, sampleSize: ActionSampleSize?): Sequence<ActionResult<T, P>> {
        return availableActions(playerIndex, sampleSize).asSequence().map { action ->
            ActionResult(action, actionType).also { it.addRequires("(deprecated allActions)", Unit, true) }
        }
    }
    fun availableActions(playerIndex: Int, sampleSize: ActionSampleSize?): Iterable<Actionable<T, P>>
    fun checkAllowed(actionable: Actionable<T, P>): ActionResult<T, P> {
        return ActionResult(actionable, actionType).also { it.addRequires("(deprecated)", Unit, actionAllowed(actionable)) }
    }
    fun actionAllowed(action: Actionable<T, P>): Boolean
    fun perform(action: Actionable<T, P>): ActionResult<T, P> {
        return ActionResult(action, actionType).also {
            it.addEffect("(deprecated perform)", Unit, performAction(action) is FlowStep.ActionPerformed<*>)
        }
    }
    fun performAction(action: Actionable<T, P>): FlowStep.ActionResultStep
    fun createAction(playerIndex: Int, parameter: P): Actionable<T, P>
    fun actionInfoKeys(playerIndex: Int, previouslySelected: List<Any>): List<ActionInfoKey> = withChosen(playerIndex, previouslySelected).actionKeys()
    fun withChosen(playerIndex: Int, chosen: List<Any>): ActionComplexChosenStep<T, P>
    fun ruleChecks() {}
}

@Deprecated("Use an ActionComplexImpl-related class instead")
data class ActionInfoKey(val serialized: Any, val actionType: String, val highlightKeys: List<Any>, val isParameter: Boolean)
@Deprecated("Use an ActionComplexImpl-related class instead")
data class ActionInfoByKey(val keys: Map<Any, List<ActionInfoKey>>) {
    operator fun plus(other: ActionInfoByKey): ActionInfoByKey {
        return ActionInfoByKey(keys.toMutableMap().mergeWith(other.keys) { a, b -> (a ?: emptyList()) + (b ?: emptyList()) })
    }
}
data class ActionSampleSize(val sampleSizes: List<Int>) {
    fun nextSample(): Pair<Int, ActionSampleSize> {
        if (sampleSizes.size == 1) {
            // When there is only one left in the sample size, continue using that number for infinity
            return sampleSizes.first() to ActionSampleSize(sampleSizes)
        }
        return sampleSizes.first() to ActionSampleSize(sampleSizes.subList(1, sampleSizes.size))
    }
}

class ActionTypeImplEntry<T : Any, P : Any>(
    val gameContext: GameRuleContext<T>,
    val actionType: ActionType<T, P>,
    val impl: GameLogicActionType<T, P>
) {
    override fun toString(): String = "ActionType:${actionType.name}"
    fun availableActions(playerIndex: Int, sampleSize: ActionSampleSize?): Iterable<Actionable<T, P>> = impl.availableActions(playerIndex, sampleSize)
    fun perform(playerIndex: Int, parameter: P) = this.perform(this.createAction(playerIndex, parameter))
    fun perform(action: Actionable<T, P>) = impl.perform(action)
    fun createAction(playerIndex: Int, parameter: P): Actionable<T, P> = impl.createAction(playerIndex, parameter)
    fun isAllowed(action: Actionable<T, P>): Boolean = impl.actionAllowed(action)
    fun isComplex(): Boolean = impl.isComplex()

    fun createActionFromSerialized(playerIndex: Int, serialized: Any): Actionable<T, P> {
        val actionOptionsContext = actionOptionsContext(playerIndex)
        if (actionType.parameterType == Unit::class) {
            // Deserialization may create a new `Unit` object, despite Unit being a singleton
            check(serialized is Unit)
            return createAction(actionOptionsContext.playerIndex, Unit as P)
        }
        if (actionType.parameterType == actionType.serializedType) {
            return createAction(actionOptionsContext.playerIndex, serialized as P)
        }

        val parameter = actionType.deserialize(actionOptionsContext, serialized)
        return if (parameter == null) {
            // If there is serialization but no deserialization is specified,
            // then check all available actions and match against those that serializes to the same value
            val actions = availableActions(actionOptionsContext.playerIndex, null).filter { action2 ->
                actionType.serialize(action2.parameter) == serialized
            }.distinct()
            if (actions.isEmpty()) {
                throw IllegalStateException("Actions available: ${actions.size} for player $playerIndex " +
                        "actionType '${this.actionType.name}' serialized parameter: $serialized")
            }
            actions.random() // Sanity checks will detect if this is okay or not.
        } else {
            createAction(actionOptionsContext.playerIndex, parameter)
        }
    }

    fun actionOptionsContext(playerIndex: Int): ActionOptionsContext<T>
        = ActionOptionsContext(gameContext, this.actionType.name, playerIndex)

    @Deprecated("to be removed")
    fun actionInfoKeys(playerIndex: Int, previouslySelected: List<Any>): ActionInfoByKey {
        return ActionInfoByKey(impl.actionInfoKeys(playerIndex, previouslySelected).groupBy { it.serialized })
    }

    fun withChosen(playerIndex: Int, chosen: List<Any>) = impl.withChosen(playerIndex, chosen)
    fun checkAllowed(actionable: Actionable<T, P>): ActionResult<T, P> = impl.checkAllowed(actionable)

    val name: String
        get() = actionType.name
    val parameterClass: KClass<P>
        get() = actionType.parameterType
}

interface Actions<T: Any> {
    val actionTypes: Set<String>
    fun types(): Set<ActionTypeImplEntry<T, Any>>
    operator fun get(actionType: String): ActionTypeImplEntry<T, Any>?
    fun <A: Any> type(actionType: ActionType<T, A>): ActionTypeImplEntry<T, A>?
    fun type(actionType: String): ActionTypeImplEntry<T, Any>?
    fun <P : Any> type(actionType: String, clazz: KClass<P>): ActionTypeImplEntry<T, P>?
    fun allActionInfo(playerIndex: Int, previouslySelected: List<Any>): ActionInfoByKey {
        return types().fold(ActionInfoByKey(emptyMap())) { acc, next ->
            acc + next.actionInfoKeys(playerIndex, previouslySelected)
        }
    }
    val choices: ActionChoices
}

class ActionChoices {
    private val players = mutableMapOf<Int, ActionPlayerChoice>()

    fun setChosen(playerIndex: Int, actionType: String?, chosen: List<Any>) {
        if (actionType != null) {
            this.players[playerIndex] = ActionPlayerChoice(actionType, chosen)
        } else {
            this.players.remove(playerIndex)
        }
    }

    fun getChosen(playerIndex: Int) = players[playerIndex]

}

class ActionsImpl<T : Any>(
    private val model: T,
    private val rules: GameActionRulesContext<T>,
    private val replayState: ReplayState
): Actions<T> {
    override val choices = ActionChoices()

    override val actionTypes: Set<String> get() = rules.actionTypes()

    override fun types(): Set<ActionTypeImplEntry<T, Any>> {
        return actionTypes.map { type(it)!! }.toSet()
    }

    override operator fun get(actionType: String): ActionTypeImplEntry<T, Any>? {
        return type(actionType)
    }

    override fun <A: Any> type(actionType: ActionType<T, A>) = rules.actionType(actionType.name) as ActionTypeImplEntry<T, A>
    override fun type(actionType: String): ActionTypeImplEntry<T, Any>? = rules.actionType(actionType)
    override fun <P : Any> type(actionType: String, clazz: KClass<P>): ActionTypeImplEntry<T, P>? {
        val entry = this.type(actionType)
        if (entry != null) {
            if (entry.actionType.parameterType != clazz) {
                throw IllegalArgumentException("ActionType '$actionType' has parameter ${entry.actionType.parameterType} and not $clazz")
            }
            return this.type(actionType) as ActionTypeImplEntry<T, P>
        }
        return null
    }

    fun <A: Any> perform(action: Actionable<T, A>): ActionResult<T, A> {
        val entry = type(action.actionType) as ActionTypeImplEntry<T, A>?
            ?: return ActionResult(action, null).also { it.addPrecondition("(actionType)", null, false) }
        return entry.perform(action)
    }

}