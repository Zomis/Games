package net.zomis.games.dsl.flow.actions

import net.zomis.games.PlayerEliminationsRead
import net.zomis.games.dsl.*
import net.zomis.games.dsl.impl.*
import kotlin.properties.PropertyDelegateProvider
import kotlin.reflect.KClass

// TODO: Decide on choices -- ephemeral, or saved? If saved, then action saving will be dramatically refactored and replays broken

//data class SmartAction(val choices: Map<String, Any>)

class SmartActionLogic<T: Any, A: Any>(
    val gameContext: GameRuleContext<T>,
    override val actionType: ActionType<T, A>
) : GameLogicActionType<T, A>, SmartActionChangeScope<T, A> {
    private val _handlers = mutableListOf<SmartActionBuilder<T, A>>()
    override val handlers get() = _handlers.toList().asSequence()

    override fun isComplex(): Boolean = true

    override fun availableActions(playerIndex: Int, sampleSize: ActionSampleSize?): Iterable<Actionable<T, A>> {
        // TODO: For multiple options, find optionalChoices, start with those and then go to required choices and just do the first one recursively
        val choices = _handlers.flatMap { it._choices.entries }
        check(choices.size == 1) { "Only single choices supported so far" }
        val choice = choices.single().value
        check(!choice.optional) { "Optional choices not supported yet" }

        return ActionComplexImpl(actionType, createContext(playerIndex), choice.options).start().depthFirstActions(sampleSize).map { it.parameter }.asIterable()
            .map { createAction(playerIndex, it) }
            .filter { this.actionAllowed(it) }
            .asIterable()
    }

    private fun createContext(playerIndex: Int): ActionOptionsContext<T> {
        return ActionOptionsContext(gameContext.game, actionType.name, playerIndex, gameContext.eliminations, gameContext.replayable)
    }
    private fun createActionContext(playerIndex: Int, parameter: A): ActionRuleContext<T, A> {
        return ActionRuleContext(gameContext.game, createAction(playerIndex, parameter), gameContext.eliminations, gameContext.replayable)
    }

    override fun withChosen(playerIndex: Int, chosen: List<Any>): ActionComplexChosenStep<T, A> {
        // Parallelize choices, so that you can choose key:"x", key:"y", number:123, number:456, in any order

        TODO("Not yet implemented")
    }

    override fun createAction(playerIndex: Int, parameter: A): Actionable<T, A>
        = Action(gameContext.game, playerIndex, actionType.name, parameter)

    override fun performAction(action: Actionable<T, A>): FlowStep.ActionResult {
        _handlers.forEach { handler ->
            handler.effect.forEach {
                it.perform(createActionContext(action.playerIndex, action.parameter))
            }
        }
        return FlowStep.ActionPerformed(action as Actionable<T, Any>, actionType as ActionType<T, Any>, gameContext.replayable.stateKeeper.lastMoveState())
    }

    override fun actionAllowed(action: Actionable<T, A>): Boolean {
        return true
//        _handlers.flatMap { it.requires }.all { it.or() }
    }

    override fun ruleChecks() {
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
    action: ActionType<T, A>, gameRuleContext: GameRuleContext<T>,
): SmartActionBuilder<T, A>()

class ActionUsingBuilder<T: Any, A: Any, E>(
    private val handler: SmartActionBuilder<T, A>,
    private val converter: SmartActionUsingScope<T, A>.() -> E
): SmartActionUsingBuilder<T, A, E> {
    override fun perform(function: ActionRuleScope<T, A>.(E) -> Unit): ActionEffect<T, A, E> {
        return ActionEffect(converter, function).also { handler._effect.add(it as ActionEffect<T, A, out Any>) }
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

private fun <K, V> MutableMap<K, V>.putSingle(key: K, v: V) {
    check(!this.containsKey(key))
    this[key] = v
}

class ActionPrecondition<T: Any, E> {
    fun or(other: ActionPrecondition<T, E>) {}
}
class ActionRequirement<T: Any, A: Any, E> {
    fun or(other: ActionRequirement<T, A, E>) {}
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
        val scope = object : SmartActionUsingScope<T, A> {
            override val game: T get() = context.game
            override val action: Actionable<T, A> = context.action
            override val eliminations: PlayerEliminationsRead = context.eliminations
        }
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


interface ActionSpecScope<T: Any, A: Any> {
    val actionType: ActionType<T, A>
    fun <E> precondition(): PropertyDelegateProvider<Any?, ActionPrecondition<T, E>>
}

interface ActionThingy<E> {
    val value: E
    fun modify(modifier: (E) -> E)
    fun disable()
    fun execute(): ActionCheckResult<E>
}

class ActionCheckResult<E>(val value: E, result: Any?) {
    val approved = when (result) {
        null -> true
        Unit -> true
        is String -> result.isNotBlank()
        is Collection<*> -> result.isNotEmpty()
        is Array<*> -> result.isNotEmpty()
        is Boolean -> result
        else -> false
    }
    val deniedResult = if (approved) null else result

}
