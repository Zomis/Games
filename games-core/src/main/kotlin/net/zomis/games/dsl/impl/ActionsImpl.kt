package net.zomis.games.dsl.impl

import net.zomis.games.dsl.*
import kotlin.reflect.KClass

interface GameLogicActionType<T : Any, A : Any> {
    val actionType: String
    fun availableActions(playerIndex: Int): Iterable<Actionable<T, A>>
    fun actionAllowed(action: Actionable<T, A>): Boolean
    fun performAction(action: Actionable<T, A>)
    fun createAction(playerIndex: Int, parameter: A): Actionable<T, A>
}

class GameLogicActionType2D<T : Any, P : Any>(override val actionType: String, private val model: T, grid: GridDsl<T, P>,
          private val replayState: ReplayState): Action2DScope<T, P>, GameLogicActionType<T, Point> {
    var allowedCheck: (Action2D<T, P>) -> Boolean = { true }
    lateinit var effect: EffectScope.(Action2D<T, P>) -> Unit
    val gridSpec = GameGridBuilder<T, P>(model)
    init {
        grid(gridSpec)
    }

    val size: Pair<Int, Int>
        get() = gridSpec.sizeX(model) to gridSpec.sizeY(model)

    override fun allowed(condition: (Action2D<T, P>) -> Boolean) {
        this.allowedCheck = condition
    }
    override fun effect(effect: EffectScope.(Action2D<T, P>) -> Unit) {
        this.effect = effect
    }

    fun getter(x: Int, y: Int): P {
        return gridSpec.get(model, x, y)
    }

    override fun actionAllowed(action: Actionable<T, Point>): Boolean {
        return this.allowedCheck(createAction(action.playerIndex, action.parameter))
    }

    override fun performAction(action: Actionable<T, Point>) {
        return this.effect(replayState, createAction(action.playerIndex, action.parameter))
    }

    override fun createAction(playerIndex: Int, parameter: Point): Action2D<T, P> {
        return Action2D(model, playerIndex, actionType, parameter.x, parameter.y, this.getter(parameter.x, parameter.y))
    }

    override fun availableActions(playerIndex: Int): Iterable<Actionable<T, Point>> {
        return (0 until this.size.second).flatMap {y ->
            (0 until this.size.first).mapNotNull { x ->
                val target = this.getter(x, y)
                val action = Action2D(model, playerIndex, actionType, x, y, target)
                val allowed = this.allowedCheck(action)
                return@mapNotNull if (allowed) action else null
            }
        }
    }
}

class GameLogicActionTypeUnit<T : Any>(override val actionType: String, private val model: T,
           private val replayState: ReplayState): ActionScope<T, Unit>, GameLogicActionType<T, Unit> {
    var allowedCheck: (Action<T, Unit>) -> Boolean = { true }
    lateinit var effect: EffectScope.(Action<T, Unit>) -> Unit
    var replayEffect: (ReplayScope.(Action<T, Unit>) -> Unit)? = null

    override fun allowed(condition: (Action<T, Unit>) -> Boolean) {
        this.allowedCheck = condition
    }
    override fun effect(effect: EffectScope.(Action<T, Unit>) -> Unit) {
        this.effect = effect
    }
    override fun replayEffect(effect: ReplayScope.(Action<T, Unit>) -> Unit) {
        this.replayEffect = effect
    }
    override fun actionAllowed(action: Actionable<T, Unit>): Boolean {
        return this.allowedCheck(createAction(action.playerIndex, action.parameter))
    }

    override fun performAction(action: Actionable<T, Unit>) {
        return this.effect(replayState, createAction(action.playerIndex, action.parameter))
    }

    override fun createAction(playerIndex: Int, parameter: Unit): Action<T, Unit> {
        return Action(model, playerIndex, actionType, parameter)
    }

    override fun availableActions(playerIndex: Int): Iterable<Actionable<T, Unit>> {
        val action = Action(model, playerIndex, actionType, Unit)
        val allowed = this.allowedCheck(action)
        return if (allowed) listOf(action) else emptyList()
    }
}

class GameLogicActionTypeComplexNext<T : Any, A : Any>(val yielder: (A) -> Unit): ActionComplexScopeResultNext<T, A> {
    override fun actionParameter(action: A) {
        yielder(action)
    }

    override fun <E : Any> option(options: Array<E>, next: ActionComplexScopeResultNext<T, A>.(E) -> Unit) {
        options.forEach {
            val nextScope = GameLogicActionTypeComplexNext<T, A>(yielder)
            next.invoke(nextScope, it)
        }
    }

}

class GameLogicActionTypeComplex<T : Any, A : Any>(override val actionType: String, private val model: T,
          private val replayState: ReplayState): ActionComplexScope<T, A>, GameLogicActionType<T, A> {

    var allowedCheck: (Action<T, A>) -> Boolean = { true }
    lateinit var effect: EffectScope.(Action<T, A>) -> Unit
    var replayEffect: (ReplayScope.(Action<T, A>) -> Unit)? = null
    private lateinit var options: ActionComplexScopeResultStart<T, A>.() -> Unit

    override fun options(options: ActionComplexScopeResultStart<T, A>.() -> Unit) {
        this.options = options
    }

    override fun allowed(condition: (Action<T, A>) -> Boolean) {
        this.allowedCheck = condition
    }

    override fun effect(effect: EffectScope.(Action<T, A>) -> Unit) {
        this.effect = effect
    }

    override fun replayEffect(effect: ReplayScope.(Action<T, A>) -> Unit) {
        this.replayEffect = effect
    }

    override fun availableActions(playerIndex: Int): Iterable<Actionable<T, A>> {
        val result = mutableListOf<A>()
        val yielder: (A) -> Unit = { result.add(it) }
        val nextScope = GameLogicActionTypeComplexNext<T, A>(yielder)
        this.options.invoke(nextScope)
        return result.map { createAction(playerIndex, it) }
    }

    override fun actionAllowed(action: Actionable<T, A>): Boolean
        = this.allowedCheck(createAction(action.playerIndex, action.parameter))

    override fun performAction(action: Actionable<T, A>) {
        this.effect(replayState, createAction(action.playerIndex, action.parameter))
    }

    override fun createAction(playerIndex: Int, parameter: A): Action<T, A>
        = Action(model, playerIndex, actionType, parameter)

}

class GameLogicActionTypeSimple<T : Any, P : Any>(override val actionType: String, private val model: T,
          private val options: (T) -> Iterable<P>,
          private val replayState: ReplayState): ActionScope<T, P>, GameLogicActionType<T, P> {
    var allowedCheck: (Action<T, P>) -> Boolean = { true }
    var replayEffect: (ReplayScope.(Action<T, P>) -> Unit)? = null
    lateinit var effect: EffectScope.(Action<T, P>) -> Unit

    override fun allowed(condition: (Action<T, P>) -> Boolean) {
        this.allowedCheck = condition
    }
    override fun effect(effect: EffectScope.(Action<T, P>) -> Unit) {
        this.effect = effect
    }
    override fun replayEffect(effect: ReplayScope.(Action<T, P>) -> Unit) {
        this.replayEffect = effect
    }
    override fun actionAllowed(action: Actionable<T, P>): Boolean {
        return this.allowedCheck(createAction(action.playerIndex, action.parameter))
    }

    override fun performAction(action: Actionable<T, P>) {
        return this.effect(replayState, createAction(action.playerIndex, action.parameter))
    }

    override fun createAction(playerIndex: Int, parameter: P): Action<T, P> {
        return Action(model, playerIndex, actionType, parameter)
    }

    override fun availableActions(playerIndex: Int): Iterable<Actionable<T, P>> {
        return options(model).mapNotNull {option ->
            val action = Action(model, playerIndex, actionType, option)
            val allowed = this.allowedCheck(action)
            return@mapNotNull action.takeIf { allowed }
        }
    }
}

class GameLogicContext<T : Any>(private val model: T, private val replayState: ReplayState) : GameLogic<T> {
    val actions = mutableMapOf<ActionType<*>, GameLogicActionType<T, *>>()
    var winner: (T) -> PlayerIndex = { null }

    override fun <P : Any> action2D(actionType: ActionType<Point>, grid: GridDsl<T, P>, logic: ActionLogic2D<T, P>) {
        val context = GameLogicActionType2D(actionType.name, model, grid, replayState)
        logic(context)
        actions[actionType] = context
    }

    override fun <A : Any> singleTarget(actionType: ActionType<A>, options: (T) -> Iterable<A>, logic: ActionLogicSingleTarget<T, A>) {
        val context = GameLogicActionTypeSimple(actionType.name, model, options, replayState)
        logic(context)
        actions[actionType] = context
    }

    override fun simpleAction(actionType: ActionType<Unit>, logic: ActionLogicSimple<T>) {
        val context = GameLogicActionTypeUnit(actionType.name, model, replayState)
        logic(context)
        actions[actionType] = context
    }

    override fun <A : Any> action(actionType: ActionType<A>, logic: ActionLogicAdvanced<T, A>) {
        val context = GameLogicActionTypeComplex<T, A>(actionType.name, model, replayState)
        logic(context)
        actions[actionType] = context
    }

    override fun intAction(actionType: ActionType<Int>, options: (T) -> Iterable<Int>, logic: ActionLogicInt<T>) {
        val context = GameLogicActionTypeSimple(actionType.name, model, options, replayState)
        logic(context)
        actions[actionType] = context
    }

    override fun winner(function: (T) -> PlayerIndex) {
        this.winner = function
    }

//    fun actionSimple(name: String, logic: ActionLogicSimple<T>) {}
//    fun action(name: String, options: ActionOptions<A>, logic: ActionLogic<A>) {}

}

class ActionTypeImplEntry<T : Any, A : Any>(private val model: T,
        private val replayState: ReplayState,
        private val actionType: ActionType<A>,
        private val impl: GameLogicActionType<T, A>) {
    fun availableActions(playerIndex: Int): Iterable<Actionable<T, A>> = impl.availableActions(playerIndex)
    fun perform(playerIndex: Int, parameter: A) {
        this.perform(this.createAction(playerIndex, parameter))
    }
    fun perform(action: Actionable<T, A>) {
        replayState.resetLastMove()
        impl.performAction(action)
    }
    fun createAction(playerIndex: Int, parameter: A): Actionable<T, A> = impl.createAction(playerIndex, parameter)
    fun isAllowed(action: Actionable<T, A>): Boolean = impl.actionAllowed(action)

    val name: String
        get() = actionType.name
    val parameterClass: KClass<A>
        get() = actionType.parameterType
}

class ActionsImpl<T : Any>(private val model: T,
                           private val logic: GameLogicContext<T>,
                           private val replayState: ReplayState) {

    val actionTypes: Set<String>
        get() = logic.actions.keys.map { it.name }.toSet()

    fun types(): Set<ActionTypeImplEntry<T, Any>> {
        return actionTypes.map { type(it)!! }.toSet()
    }

    operator fun get(actionType: String): ActionTypeImplEntry<T, Any>? {
        return type(actionType)
    }

    fun type(actionType: String): ActionTypeImplEntry<T, Any>? {
        return logic.actions.entries.find { it.key.name == actionType }?.let {
            ActionTypeImplEntry(model, replayState, it.key as ActionType<Any>, it.value as GameLogicActionType<T, Any>)
        }
    }
    fun <A : Any> type(actionType: String, clazz: KClass<T>): ActionTypeImplEntry<T, A>? {
        val entry = logic.actions.entries.find { it.key.name == actionType }
        if (entry != null) {
            if (entry.key.parameterType != clazz) {
                throw IllegalArgumentException("ActionType '$actionType' has parameter ${entry.key.parameterType} and not $clazz")
            }
            return this.type(actionType) as ActionTypeImplEntry<T, A>
        }
        return null
    }

    fun lastMoveState(): Map<String, Any?> {
        return replayState.lastMoveState()
    }

}