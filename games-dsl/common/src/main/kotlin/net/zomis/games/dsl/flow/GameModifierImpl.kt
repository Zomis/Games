package net.zomis.games.dsl.flow

import net.zomis.games.dsl.*
import net.zomis.games.dsl.events.*
import net.zomis.games.dsl.impl.LogActionContext
import net.zomis.games.dsl.impl.LogContext
import net.zomis.games.rules.Rule
import net.zomis.games.rules.RuleSpec
import net.zomis.games.rules.StateOwner
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

class ActionModifier<GameModel: Any>(
    val action: ActionType<GameModel, Any>,
    val definition: GameFlowActionScope<GameModel, Any>.() -> Unit
)

// TODO: Stateful rules and Stateless rules. Stateless has no owner and no internal state.

class GameModifierImpl<GameModel: Any, Owner>(
    private val meta: GameMetaScope<GameModel>,
    val owner: Owner,
    val ruleSpec: RuleSpec<GameModel, Owner>,
    private val stateOwner: StateOwner,
): GameModifierScope<GameModel, Owner>, StateOwner by stateOwner {
    private var active: Boolean = true

    override val ruleHolder: Owner get() = owner
    override val game: GameModel get() = meta.game
    private val actionTypesEnabled = mutableMapOf<ActionType<GameModel, out Any>, Boolean>()
    private val activationEffects = mutableListOf<GameModifierApplyScope<GameModel, Owner>.() -> Unit>()
    private val stateChecksBeforeAction = mutableListOf<GameModifierApplyScope<GameModel, Owner>.() -> Unit>()
    private val actionModifiers = mutableListOf<ActionModifier<GameModel>>()
    private val activeConditions = mutableListOf<GameModifierScope<GameModel, Owner>.() -> Boolean>()
    private var removeCondition: (GameModifierScope<GameModel, Owner>.() -> Boolean)? = null
    private val globalPreconditions = mutableListOf<ActionOptionsScope<GameModel>.() -> Boolean>()
    private val subRules = mutableListOf<GameModifierImpl<GameModel, out Any?>>()

    override fun enableAction(actionType: ActionType<GameModel, out Any>) {
        actionTypesEnabled[actionType] = true
    }

    override fun applyRule(condition: () -> Boolean, rule: RuleSpec<GameModel, out Any>): Rule<GameModel, out Any> {
        TODO("Not yet implemented")
    }

    override fun <Owner2> subRule(rule: RuleSpec<GameModel, Owner2>, owner: Owner2, stateOwner: StateOwner): Rule<GameModel, Owner2> {
        val context = GameModifierImpl(meta, owner, rule, stateOwner)
        rule.invoke(context)
        subRules.add(context)
        return context
    }

    override fun overrides(rule: Rule<GameModel, out Any>) {
        TODO("Not yet implemented")
    }

    override fun conflictsWith(rule: Rule<GameModel, out Any>) {
        TODO("Not yet implemented")
    }

    override fun onNoActions(function: () -> Unit) = meta.onNoActions(function)

    override fun onState(condition: () -> Boolean, thenPerform: GameModifierApplyScope<GameModel, Owner>.() -> Unit) {
        stateCheckBeforeAction {
            if (condition.invoke()) {
                thenPerform.invoke(this)
            }
        }
    }

    override fun activeWhile(condition: GameModifierScope<GameModel, Owner>.() -> Boolean) {
        this.activeConditions.add(condition)
    }

    override fun removeWhen(condition: GameModifierScope<GameModel, Owner>.() -> Boolean) {
        check(this.removeCondition == null)
        this.removeCondition = condition
    }

    override fun <E : Any> on(event: EventFactory<E>, priority: EventPriority): EventModifierScope<GameModel, E> {
        val impl = EventModifierImpl<GameModel, E>(meta) { this.isActive() && it.effectSource == event }
        meta.events.addTemporaryEventListener(priority, impl)
        return impl
    }

    override fun <E : Any> on(eventType: KClass<E>, priority: EventPriority): EventModifierScope<GameModel, E> {
        val impl = EventModifierImpl<GameModel, E>(meta) { this.isActive() && eventType.isInstance(it.event) }
        meta.events.addTemporaryEventListener(priority, impl)
        return impl
    }

    override fun <C : Any> config(config: GameConfig<C>): C = meta.config(config)

    override fun <A : Any> action(action: ActionDefinition<GameModel, A>): ActionRule<GameModel, A> {
        TODO("Not yet implemented")
    }

    override fun <A : Any> action(
        action: ActionDefinition<GameModel, A>,
        definition: GameFlowActionScope<GameModel, A>.() -> Unit
    ) {
        TODO("Not yet implemented")
    }

    override fun <A : Any> action(
        action: ActionType<GameModel, A>,
        definition: GameFlowActionScope<GameModel, A>.() -> Unit
    ) {
        this.actionModifiers.add(
            ActionModifier(action as ActionType<GameModel, Any>, definition as GameFlowActionScope<GameModel, Any>.() -> Unit)
        )
    }

    override fun allActionsPrecondition(precondition: ActionOptionsScope<GameModel>.() -> Boolean) {
        this.globalPreconditions.add(precondition)
    }

    override fun stateCheckBeforeAction(doSomething: GameModifierApplyScope<GameModel, Owner>.() -> Unit) {
        this.stateChecksBeforeAction.add(doSomething)
    }

    override fun onActivate(doSomething: GameModifierApplyScope<GameModel, Owner>.() -> Unit) {
        this.activationEffects.add(doSomething)
    }

    fun executeOnActivate() {
        val context = createApplyContext()
        for (effect in this.activationEffects) {
            effect.invoke(context)
        }
    }

    private fun createApplyContext(): GameModifierApplyContext<GameModel, Owner>
        = GameModifierApplyContext(ruleHolder, meta)

    fun executeBeforeAction() = this.executeStateCheck(stateChecksBeforeAction)

    private fun executeStateCheck(checks: List<GameModifierApplyScope<GameModel, Owner>.() -> Unit>) {
        if (!this.isActive()) return
        if (this.removeCondition?.invoke(this) == true) {
            meta.removeRule(this)
        }

        val context = createApplyContext()
        for (effect in checks) {
            effect.invoke(context)
        }

        this.actionModifiers.forEach {
            meta.addAction(it.action, it.definition)
        }
        this.globalPreconditions.forEach(meta::addGlobalActionPrecondition)
        subRules.forEach { it.executeBeforeAction() }
    }

    fun isActive(): Boolean {
        if (!active) return false
        return this.activeConditions.all { it.invoke(this) }
    }

    fun fire() {
        clear()
        this.active = true
        ruleSpec.invoke(this)
        executeBeforeAction()
    }

    fun disable() {
        this.active = false
        subRules.forEach {
            it.disable()
        }
    }

    private fun clear() {
        actionTypesEnabled.clear()
        // states.clear() // Do not clear states.
        activationEffects.clear()
        stateChecksBeforeAction.clear()
        actionModifiers.clear()
        activeConditions.clear()
        removeCondition = null
        globalPreconditions.clear()
        subRules.clear()
    }

    override fun toString(): String = "Rule(ruleSpec=$ruleSpec, owner=$owner, state=$stateOwner)"

}

class GameModifierApplyContext<GameModel : Any, Owner>(
    override val ruleHolder: Owner,
    override val meta: GameMetaScope<GameModel>
) : GameModifierApplyScope<GameModel, Owner> {
    override val game: GameModel get() = meta.game

    override fun log(logging: LogScope<GameModel>.() -> String) {
        meta.replayable.stateKeeper.log(LogContext(game, null).log(logging))
    }

    override fun logSecret(playerIndex: Int, logging: LogScope<GameModel>.() -> String): LogSecretScope<GameModel> {
        val context = LogContext(game, playerIndex).secretLog(playerIndex, logging)
        meta.replayable.stateKeeper.log(context)
        return context
    }
}

class EventModifierImpl<GameModel: Any, E: Any>(
    val meta: GameMetaScope<GameModel>,
    val filter: (GameEventEffectScope<GameModel, E>) -> Boolean
) : EventModifierScope<GameModel, E>, EventListener {
    private val conditions = mutableListOf<GameEventEffectScope<GameModel, E>.() -> Boolean>()
    private val mutations = mutableListOf<GameEventEffectScope<GameModel, E>.() -> E>()
    private val effects = mutableListOf<GameEventEffectScope<GameModel, E>.() -> Unit>()

    override fun require(condition: GameEventEffectScope<GameModel, E>.() -> Boolean) {
        this.conditions.add(condition)
    }

    override fun modify(function: GameEventEffectScope<GameModel, E>.() -> E) {
        this.mutations.add(function)
    }

    override fun mutate(function: GameEventEffectScope<GameModel, E>.() -> Unit) {
        this.mutations.add {
            function.invoke(this)
            this.event
        }
    }

    override fun perform(listener: GameEventEffectScope<GameModel, E>.() -> Unit) {
        this.effects.add(listener)
    }

    override fun execute(scope: GameEventEffectScope<Any, Any>) {
        if (!filterMatch(scope)) return
        this.effects.forEach {
            it.invoke(scope as GameEventEffectScope<GameModel, E>)
        }
    }

    override fun conditionCheck(scope: GameEventEffectScope<Any, Any>): Boolean {
        if (!filterMatch(scope)) return true
        return this.conditions.all {
            it.invoke(scope as GameEventEffectScope<GameModel, E>)
        }
    }

    private fun filterMatch(scope: GameEventEffectScope<Any, Any>): Boolean {
        return filter.invoke(scope as GameEventEffectScope<GameModel, E>)
    }

    override fun mutate(scope: GameEventEffectScope<Any, Any>, source: EventSource, event: Any): Any {
        if (!filterMatch(scope)) return event

        var changedEvent: E = event as E
        val newScope: GameEventEffectScope<GameModel, E> = object : GameEventEffectScope<GameModel, E> {
            override val effectSource: EventSource get() = scope.effectSource
            override val event: E get() = changedEvent
            override val meta: GameMetaScope<GameModel> get() = this@EventModifierImpl.meta
        }
        for (mutation in this.mutations) {
            changedEvent = mutation.invoke(newScope)
        }
        return changedEvent
    }

}