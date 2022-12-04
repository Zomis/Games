package net.zomis.games.dsl.flow.actions

import net.zomis.games.PlayerEliminationsRead
import net.zomis.games.api.UsageScope
import net.zomis.games.common.putSingle
import net.zomis.games.dsl.*
import net.zomis.games.dsl.flow.GameMetaScope
import net.zomis.games.dsl.impl.*
import kotlin.properties.PropertyDelegateProvider
import kotlin.reflect.KClass

// TODO: Decide on choices -- ephemeral, or saved? If saved, then action saving will be dramatically refactored and replays broken

//data class SmartAction(val choices: Map<String, Any>)

class SmartActionLogic<T: Any, A: Any>(
    val gameContext: GameMetaScope<T>,
    override val actionType: ActionType<T, A>
) : GameLogicActionType<T, A>, SmartActionChangeScope<T, A> {
    private val _handlers = mutableListOf<SmartActionBuilder<T, A>>()
    override val handlers get() = _handlers.toList().asSequence()

    override fun isComplex(): Boolean = true

    override fun availableActions(playerIndex: Int, sampleSize: ActionSampleSize?): Iterable<Actionable<T, A>> {
        if (!checkPreconditions(playerIndex)) {
            return emptyList()
        }
        // TODO: For multiple options, find optionalChoices, start with those and then go to required choices and just do the first one recursively
        val choices = _handlers.flatMap { it._choices.entries }
        if (choices.isEmpty()) {
            return emptyList()
        }
        check(choices.size == 1) { "Only single choices supported so far, found $choices (actionType $actionType) when checking actions for playerIndex $playerIndex" }
        val choice = choices.single().value
        check(!choice.optional) { "Optional choices not supported yet (actionType $actionType)" }

        return ActionComplexImpl(actionType, createOptionsContext(playerIndex), choice.options).start().depthFirstActions(sampleSize).map { it.parameter }
            .map { createAction(playerIndex, it) }
            .filter { this.actionAllowed(it) }
            .asIterable()
    }

    private fun createOptionsContext(playerIndex: Int): ActionOptionsContext<T> {
        return ActionOptionsContext(gameContext, actionType.name, playerIndex)
    }
    private fun createActionContext(playerIndex: Int, parameter: A): ActionRuleContext<T, A> {
        return ActionRuleContext(gameContext, createAction(playerIndex, parameter))
    }

    override fun withChosen(playerIndex: Int, chosen: List<Any>): ActionComplexChosenStep<T, A> {
        // TODO: Parallelize choices, so that you can choose key:"x", key:"y", number:123, number:456, in any order
        if (!checkPreconditions(playerIndex) || _handlers.flatMap { it.choices.values }.isEmpty()) {
            return ActionComplexChosenStepEmpty(actionType, playerIndex, chosen)
        }
        val choiceRule = _handlers.flatMap { it.choices.values }.single()
        return ActionComplexImpl(actionType, createOptionsContext(playerIndex), choiceRule.options).withChosen(chosen)
    }

    private fun checkPreconditions(playerIndex: Int): Boolean {
        val context = createOptionsContext(playerIndex)
        return _handlers.flatMap { it.preconditions }.all { precond -> precond.fulfilled(context) }
    }

    override fun createAction(playerIndex: Int, parameter: A): Actionable<T, A>
        = Action(gameContext.game, playerIndex, actionType.name, parameter)

    override fun performAction(action: Actionable<T, A>): FlowStep.ActionResultStep {
        _handlers.forEach { handler ->
            handler.effect.forEach {
                it.perform(createActionContext(action.playerIndex, action.parameter))
            }
        }
        return FlowStep.ActionPerformed(action as Actionable<T, Any>, actionType as ActionType<T, Any>, gameContext.replayable.stateKeeper.lastMoveState())
    }

    override fun actionAllowed(action: Actionable<T, A>): Boolean {
        val context = createActionContext(action.playerIndex, action.parameter)
        return checkPreconditions(action.playerIndex) && _handlers.flatMap { it.requires }.all { it.fulfilled(context) }
    }

    override fun checkAllowed(actionable: Actionable<T, A>): ActionResult<T, A> {
        val context = createActionContext(actionable.playerIndex, actionable.parameter)
        val result = ActionResult(actionable, actionType)
        _handlers.flatMap { it.preconditions }.forEach {
            result.add(it.check(context))
        }
        _handlers.flatMap { it.requires }.forEach {
            result.add(it.check(context))
        }
        return result
    }

    override fun ruleChecks() {
        if (this.actionType.parameterType == Unit::class && this._handlers.all { it.choices.isEmpty() }) {
            this._handlers.add(SmartActionBuilder<T, A>().also { it.choice("", false) { listOf(Unit as A) } })
        }
        // Apply modifiers
        this.handlers.forEach { handler ->
            handler._modifiers.forEach { modifier ->
                modifier.invoke(this)
            }
        }
    }

    fun add(handler: SmartActionBuilder<T, A>) {
        this._handlers.add(handler)
    }
}


class SmartActionContext<T: Any, A: Any>(
    action: ActionType<T, A>, gameRuleContext: GameMetaScope<T>,
): SmartActionBuilder<T, A>()

class ActionUsingBuilder<T: Any, A: Any, E>(
    private val handler: SmartActionBuilder<T, A>,
    private val converter: SmartActionUsingScope<T, A>.() -> E
): SmartActionUsingBuilder<T, A, E> {
    override fun perform(function: ActionRuleScope<T, A>.(E) -> Unit): ActionEffect<T, A, E> {
        return ActionEffect(converter, function).also { handler._effect.add(it as ActionEffect<T, A, out Any>) }
    }

    override fun precondition(rule: ActionOptionsScope<T>.() -> Boolean): ActionPrecondition<T, E> {
        return ActionPrecondition<T, E>(rule).also { handler._preconditions.add(it as ActionPrecondition<T, out Any>) }
    }

    override fun requires(rule: ActionRuleScope<T, A>.(E) -> Boolean): ActionRequirement<T, A, E> {
        return ActionRequirement(converter, rule).also { handler._requires.add(it as ActionRequirement<T, A, out Any>) }
    }
}

open class SmartActionBuilder<T: Any, A: Any>: SmartActionScope<T, A> {
    internal val _preconditions = mutableListOf<ActionPrecondition<T, out Any>>()
    internal val _requires = mutableListOf<ActionRequirement<T, A, out Any>>()
    internal val _choices = mutableMapOf<String, ActionChoice<T, A, out Any>>()
    internal val _effect = mutableListOf<ActionEffect<T, A, out Any>>()
    internal val _postEffect = mutableListOf<ActionEffect<T, A, out Any>>()
    internal val _modifiers = mutableListOf<SmartActionChangeScope<T, A>.() -> Unit>()
    val preconditions get() = _preconditions.toList()
    val requires get() = _requires.toList()
    val choices get() = _choices
    val effect get() = _effect.toList()
    val postEffect get() = _postEffect.toList()

    override fun <E> using(function: SmartActionUsingScope<T, A>.() -> E): SmartActionUsingBuilder<T, A, E> {
        return ActionUsingBuilder(this, function)
    }
    override fun exampleChoices(name: String, optional: Boolean, function: ActionOptionsScope<T>.() -> Iterable<A>): SmartActionChoice<A> {
        return ActionChoice<T, A, A>(name, optional, exhaustive = false, iterableToChoices(function)).also {
            _choices.putSingle(name, it as ActionChoice<T, A, out Any>)
        }
    }

    override fun choice(name: String, optional: Boolean, function: ActionOptionsScope<T>.() -> Iterable<A>): SmartActionChoice<A> {
        return ActionChoice<T, A, A>(name, optional, exhaustive = true, iterableToChoices(function)).also {
            _choices.putSingle(name, it as ActionChoice<T, A, out Any>)
            _requires.add(ActionRequirement({}, {
                action.parameter in function.invoke(this)
            }))
        }
    }

    override fun change(block: SmartActionChangeScope<T, A>.() -> Unit) {
        this._modifiers.add(block)
    }

    internal fun iterableToChoices(rule: ActionOptionsScope<T>.() -> Iterable<A>): ActionChoicesScope<T, A>.() -> Unit {
        return {
            options(rule) {
                parameter(it)
            }
        }
    }

}

class SmartActionUsingContext<T: Any, A: Any>(
    val context: ActionRuleScope<T, A>
): SmartActionUsingScope<T, A> {
    override val game: T get() = context.game
    override val action: Actionable<T, A> get() = context.action
    override val eliminations: PlayerEliminationsRead get() = context.eliminations
}

class ActionPrecondition<T: Any, E>(val rule: ActionOptionsScope<T>.() -> Boolean) {
    // If modifiers were supported here, use other scope than `SmartActionUsingScope` as it contains actions parameter

    fun check(context: ActionOptionsScope<T>): ActionResultPart<E> {
        return ActionResultPart(ActionCheckType.Precondition, this, null, rule.invoke(context))
    }

    @Deprecated("use check instead", replaceWith = ReplaceWith("check"))
    fun fulfilled(context: ActionOptionsScope<T>): Boolean {
        return rule.invoke(context)
    }
}
class ActionRequirement<T: Any, A: Any, E>(
    private val converter: SmartActionUsingScope<T, A>.() -> E,
    val rule: ActionRuleScope<T, A>.(E) -> Boolean
) {
    private val modifiers = mutableListOf<(E) -> E>()

    fun modify(function: (E) -> E) {
        this.modifiers.add(function)
    }

    fun check(context: ActionRuleContext<T, A>): ActionResultPart<E> {
        val value = converter.invoke(SmartActionUsingContext(context))
        val modifiedValue = modifiers.fold(value) { old, func -> func.invoke(old) }
        val result = rule.invoke(context, modifiedValue)
        return ActionResultPart(ActionCheckType.Requires, this, modifiedValue, result)
    }

    @Deprecated("use check instead to get detailed results", replaceWith = ReplaceWith("check"))
    fun fulfilled(context: ActionRuleContext<T, A>): Boolean = check(context).approved
}
class ActionCost<T: Any, A: Any, E> {
    // Choose how to pay some costs? (Colored mana, coins with wildcards, "you may do X instead of paying Y"...)
}
class ActionEffect<T: Any, A: Any, E>(
    private val converter: SmartActionUsingScope<T, A>.() -> E,
    val function: ActionRuleScope<T, A>.(E) -> Unit
) {
    private val modifiers = mutableListOf<(E) -> E>()

    fun modify(function: (E) -> E) {
        this.modifiers.add(function)
    }

    fun perform(context: ActionRuleContext<T, A>) {
        val scope = SmartActionUsingContext(context)
        val value = converter.invoke(scope)
        val modifiedValue = modifiers.fold(value) { old, func -> func.invoke(old) }
        function.invoke(context, modifiedValue)
    }

    fun <K: Any> ofType(type: KClass<K>): ActionEffect<T, A, K> = this as ActionEffect<T, A, K>

}
class ActionChoice<T: Any, A: Any, E>(
    val key: String,
    val optional: Boolean,
    val exhaustive: Boolean,
    val options: ActionChoicesScope<T, A>.() -> Unit
): SmartActionChoice<E> {
    fun allChoices(chosen: List<Any>): Iterable<E> {
        TODO("Not yet implemented")
    }
    // List of required ActionChoices
}

/*
* add preconditions, requirements, costs, effects, etc. to a list
*
* allow other ActionBuilders (or specs or whatever) to interact with the list, to disable, modify values, and more
*
* allow multiple parallel choices, using `val player by choice { ... }` and later `chosen(player)`
*   - allow chosen(x) to suspend until x is chosen?
*
* save param, or save chosens?
* how to convert chosen to param?
*
*/


interface ActionSpecScope<T: Any, A: Any> : UsageScope {
    val actionType: ActionType<T, A>
    fun <E> precondition(): PropertyDelegateProvider<Any?, ActionPrecondition<T, E>>
}

interface ActionThingy<E> {
    val value: E
    fun modify(modifier: (E) -> E)
    fun disable()
    fun execute(): ActionResultPart<E>
}
