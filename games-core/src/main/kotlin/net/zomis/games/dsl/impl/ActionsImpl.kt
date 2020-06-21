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

abstract class GameLogicActionTypeBase<T : Any, P : Any, A : Actionable<T, P>>(
        val model: T,
        val replayState: ReplayState): ActionScope<T, P, A>, GameLogicActionType<T, P, A> {

    var allowedCheck: (A) -> Boolean = { true }
    lateinit var effect: EffectScope.(A) -> Unit
    var replayEffect: (ReplayScope.(A) -> Unit)? = null

    override fun replayEffect(effect: ReplayScope.(A) -> Unit) {
        this.replayEffect = effect
    }

    override fun allowed(condition: (A) -> Boolean) {
        this.allowedCheck = condition
    }
    override fun effect(effect: EffectScope.(A) -> Unit) {
        this.effect = effect
    }

    override fun actionAllowed(action: A): Boolean {
        return this.allowedCheck(action)
    }

    override fun replayAction(action: A, state: Map<String, Any>?) {
        if (state != null) {
            replayState.setReplayState(state)
        }
        if (this.replayEffect != null) {
            this.replayEffect!!(replayState, action)
        } else {
            this.performAction(action)
        }
    }

    override fun performAction(action: A) {
        return this.effect(replayState, action)
    }

}

class GameLogicActionType2D<T : Any, P : Any>(override val actionType: String,
          model: T, grid: GridDsl<T, P>,
          replayState: ReplayState): GameLogicActionTypeBase<T, Point, Action2D<T, P>>(model, replayState) {

    val gridSpec = GameGridBuilder<T, P>(model)
    init {
        grid(gridSpec)
    }

    val size: Pair<Int, Int>
        get() = gridSpec.sizeX(model) to gridSpec.sizeY(model)

    fun getter(x: Int, y: Int): P {
        return gridSpec.get(model, x, y)
    }

    override fun createAction(playerIndex: Int, parameter: Point): Action2D<T, P> {
        return Action2D(model, playerIndex, actionType, parameter.x, parameter.y, getter(parameter.x, parameter.y))
    }

    override fun availableActions(playerIndex: Int): Iterable<Action2D<T, P>> {
        return (0 until this.size.second).flatMap {y ->
            (0 until this.size.first).mapNotNull { x ->
                val target = this.getter(x, y)
                val action = Action2D(model, playerIndex, actionType, x, y, target)
                val allowed = this.actionAllowed(action)
                return@mapNotNull if (allowed) action else null
            }
        }
    }
}

class GameLogicActionTypeUnit<T : Any>(override val actionType: String,
           model: T, replayState: ReplayState): GameLogicActionTypeBase<T, Unit, Action<T, Unit>>(model, replayState) {

    override fun createAction(playerIndex: Int, parameter: Unit): Action<T, Unit> {
        return Action(model, playerIndex, actionType, parameter)
    }

    override fun availableActions(playerIndex: Int): Iterable<Action<T, Unit>> {
        val action = Action(model, playerIndex, actionType, Unit)
        val allowed = this.allowedCheck(action)
        return if (allowed) listOf(action) else emptyList()
    }
}

class GameLogicActionTypeComplexNext<T : Any, A : Any>(private val model: T, val yielder: (A) -> Unit): ActionComplexScopeResultNext<T, A> {
    override fun actionParameter(action: A) {
        yielder(action)
    }

    override fun <E : Any> option(options: Iterable<E>, next: ActionComplexScopeResultNext<T, A>.(E) -> Unit) {
        options.forEach {
            val nextScope = GameLogicActionTypeComplexNext<T, A>(model, yielder)
            next.invoke(nextScope, it)
        }
    }

    override fun <E : Any> optionFrom(options: (T) -> Iterable<E>, next: ActionComplexScopeResultNext<T, A>.(E) -> Unit) {
        this.option(options(model), next)
    }
}

class GameLogicActionTypeComplexNextOnly<T : Any, A : Any>(
        private val model: T,
        private val chosen: List<Any>,
        private val evaluateOptions: Boolean,
       private val nextYielder: (List<Any>) -> Unit, private val actionYielder: (A) -> Unit): ActionComplexScopeResultNext<T, A> {
    override fun actionParameter(action: A) {
        actionYielder(action)
    }

    override fun <E : Any> optionFrom(options: (T) -> Iterable<E>, next: ActionComplexScopeResultNext<T, A>.(E) -> Unit) {
        this.option(options(model), next)
    }

    override fun <E : Any> option(options: Iterable<E>, next: ActionComplexScopeResultNext<T, A>.(E) -> Unit) {
        if (chosen.isNotEmpty()) {
            val nextChosen = chosen[0] as E
            val nextChosenList = chosen.subList(1, chosen.size)

            val nextScope = GameLogicActionTypeComplexNextOnly<T, A>(model, nextChosenList,true, nextYielder, actionYielder)
            next.invoke(nextScope, nextChosen)
        } else {
            nextYielder(options.toList())
            if (!evaluateOptions) {
                return
            }
            options.forEach {
                val nextScope = GameLogicActionTypeComplexNextOnly<T, A>(model, emptyList(), false, {}, actionYielder)
                next.invoke(nextScope, it)
            }
        }
    }
}

class GameLogicActionTypeComplex<T : Any, A : Any>(override val actionType: String,
        model: T, replayState: ReplayState): ActionComplexScope<T, A>, GameLogicActionTypeBase<T, A, Action<T, A>>(model, replayState) {

    private lateinit var options: ActionComplexScopeResultStart<T, A>.() -> Unit

    override fun options(options: ActionComplexScopeResultStart<T, A>.() -> Unit) {
        this.options = options
    }

    fun availableOptionsNext(playerIndex: Int, chosen: List<Any>): Pair<List<Any>, List<A>> {
        val nexts = mutableListOf<Any>()
        val actionParams = mutableListOf<A>()
        val yielder: (List<Any>) -> Unit = { nexts.addAll(it) }
        val actionYielder: (A) -> Unit = { actionParams.add(it) }

        val nextScope = GameLogicActionTypeComplexNextOnly<T, A>(model, chosen, true, yielder, actionYielder)
        this.options.invoke(nextScope)
        return nexts to actionParams
    }

    override fun availableActions(playerIndex: Int): Iterable<Action<T, A>> {
        val result = mutableListOf<A>()
        val yielder: (A) -> Unit = { result.add(it) }
        val nextScope = GameLogicActionTypeComplexNext<T, A>(model, yielder)
        this.options.invoke(nextScope)
        return result.map { createAction(playerIndex, it) }.filter { this.actionAllowed(it) }
    }

    override fun createAction(playerIndex: Int, parameter: A): Action<T, A>
        = Action(model, playerIndex, actionType, parameter)

}

class GameLogicActionTypeSimple<T : Any, P : Any>(override val actionType: String,
          model: T, val options: (T) -> Iterable<P>, replayState: ReplayState): GameLogicActionTypeBase<T, P, Action<T, P>>(model, replayState) {

    override fun createAction(playerIndex: Int, parameter: P): Action<T, P> {
        return Action(model, playerIndex, actionType, parameter)
    }

    override fun availableActions(playerIndex: Int): Iterable<Action<T, P>> {
        return options(model).mapNotNull {option ->
            val action = Action(model, playerIndex, actionType, option)
            val allowed = this.allowedCheck(action)
            return@mapNotNull action.takeIf { allowed }
        }
    }
}

class GameLogicContext<T : Any>(private val model: T, private val replayState: ReplayState) : GameLogic<T> {
    val actions = mutableMapOf<ActionType<*>, GameLogicActionType<T, *, *>>()
    var winner: (T) -> PlayerIndex = { null }

    override fun <P : Any> action2D(actionType: ActionType<Point>, grid: GridDsl<T, P>, logic: ActionLogic<T, Point, Action2D<T, P>>) {
        val context = GameLogicActionType2D(actionType.name, model, grid, replayState)
        logic(context)
        actions[actionType] = context
    }

    override fun <A : Any> singleTarget(actionType: ActionType<A>, options: (T) -> Iterable<A>, logic: ActionLogic<T, A, Action<T, A>>) {
        val context = GameLogicActionTypeSimple(actionType.name, model, options, replayState)
        logic(context)
        actions[actionType] = context
    }

    override fun simpleAction(actionType: ActionType<Unit>, logic: ActionLogic<T, Unit, Action<T, Unit>>) {
        val context = GameLogicActionTypeUnit(actionType.name, model, replayState)
        logic(context)
        actions[actionType] = context
    }

    override fun <A : Any> action(actionType: ActionType<A>, logic: ActionLogicAdvanced<T, A>) {
        val context = GameLogicActionTypeComplex<T, A>(actionType.name, model, replayState)
        logic(context)
        actions[actionType] = context
    }

    override fun intAction(actionType: ActionType<Int>, options: (T) -> Iterable<Int>, logic: ActionLogic<T, Int, Action<T, Int>>) {
        val context = GameLogicActionTypeSimple(actionType.name, model, options, replayState)
        logic(context)
        actions[actionType] = context
    }

    override fun winner(function: (T) -> PlayerIndex) {
        this.winner = function
    }

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
        } else if (impl is GameLogicActionTypeComplex) {
            val actionInfo = impl.availableOptionsNext(playerIndex, previouslySelected)
            ActionInfo(actionInfo.first, actionInfo.second.map(serializer))
        } else {
            if (previouslySelected.isNotEmpty()) {
                throw IllegalArgumentException("Unable to select any options for action ${actionType.name}")
            }
            ActionInfo(emptyList(), availableActions(playerIndex).map { it.parameter }.map(serializer))
        }
    }

    fun createActionFromSerialized(actionOptionsContext: ActionOptionsContext<T>, serialized: Any): A {
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

    val name: String
        get() = actionType.name
    val parameterClass: KClass<P>
        get() = actionType.parameterType
}

class ActionsImpl<T : Any>(private val model: T,
                           private val logic: GameLogicContext<T>,
                           private val rules: GameRulesContext<T>,
                           private val replayState: ReplayState) {

    val actionTypes: Set<String>
        get() = logic.actions.keys.map { it.name }.toSet() + rules.actionTypes()

    fun types(): Set<ActionTypeImplEntry<T, Any, Actionable<T, Any>>> {
        return actionTypes.map { type(it)!! }.toSet()
    }

    operator fun get(actionType: String): ActionTypeImplEntry<T, Any, Actionable<T, Any>>? {
        return type(actionType)
    }

    fun type(actionType: String): ActionTypeImplEntry<T, Any, Actionable<T, Any>>? {
        val result = logic.actions.entries.find { it.key.name == actionType }?.let {
            ActionTypeImplEntry(model, replayState, it.key as ActionType<Any>, it.value as GameLogicActionType<T, Any, Actionable<T, Any>>)
        }
        return result ?: rules.actionType(actionType)
    }
    fun <P : Any> type(actionType: String, clazz: KClass<T>): ActionTypeImplEntry<T, P, out Actionable<T, P>>? {
        val entry = logic.actions.entries.find { it.key.name == actionType }
        if (entry != null) {
            if (entry.key.parameterType != clazz) {
                throw IllegalArgumentException("ActionType '$actionType' has parameter ${entry.key.parameterType} and not $clazz")
            }
            return this.type(actionType) as ActionTypeImplEntry<T, P, Actionable<T, P>>
        }
        return null
    }

}