package net.zomis.games.dsl.flow

import net.zomis.games.dsl.ActionOptionsScope
import net.zomis.games.dsl.ActionType
import net.zomis.games.dsl.events.*
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

class ActionModifier<GameModel: Any>(
    val action: ActionType<GameModel, Any>,
    val definition: GameFlowActionScope<GameModel, Any>.() -> Unit
)

class GameModifierImpl<GameModel: Any, Owner>(
    private val meta: GameMetaScope<GameModel>,
    val owner: Owner
): GameModifierScope<GameModel, Owner> {
    override val ruleHolder: Owner get() = owner
    override val game: GameModel get() = meta.game
    private val states = mutableMapOf<String, Any>()
    private val activationEffects = mutableListOf<GameModifierApplyScope<GameModel, Owner>.() -> Unit>()
    private val stateChecks = mutableListOf<GameModifierApplyScope<GameModel, Owner>.() -> Unit>()
    private val actionModifiers = mutableListOf<ActionModifier<GameModel>>()
    private val activeConditions = mutableListOf<GameModifierScope<GameModel, Owner>.() -> Boolean>()
    private var removeCondition: (GameModifierScope<GameModel, Owner>.() -> Boolean)? = null
    private val globalPreconditions = mutableListOf<ActionOptionsScope<GameModel>.() -> Boolean>()

    override fun <T> state(initial: () -> T): PropertyDelegateProvider<GameModifierScope<GameModel, Owner>?, Delegate<T>> {
        return this.addState(initial.invoke())
    }

    inner class Delegate<T>(var value: T): ReadWriteProperty<GameModifierScope<GameModel, Owner>?, T> {
        override fun getValue(thisRef: GameModifierScope<GameModel, Owner>?, property: KProperty<*>): T {
            return this.value
        }

        override fun setValue(thisRef: GameModifierScope<GameModel, Owner>?, property: KProperty<*>, value: T) {
            this.value = value
        }
    }
    private fun <T> addState(initial: T): PropertyDelegateProvider<GameModifierScope<GameModel, Owner>?, Delegate<T>> {
        return object : PropertyDelegateProvider<GameModifierScope<GameModel, Owner>?, Delegate<T>> {
            override fun provideDelegate(thisRef: GameModifierScope<GameModel, Owner>?, property: KProperty<*>): GameModifierImpl<GameModel, Owner>.Delegate<T> {
                println("thisRef: $thisRef")
                val propertyName = property.name
                states[propertyName] = initial as Any
                return Delegate(initial)
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
        meta.events.addEventListener(priority, impl)
        return impl
    }

    override fun <E : Any> on(eventType: KClass<E>, priority: EventPriority): EventModifierScope<GameModel, E> {
        val impl = EventModifierImpl<GameModel, E>(meta) { this.isActive() && eventType.isInstance(it.event) }
        meta.events.addEventListener(priority, impl)
        return impl
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

    override fun stateCheck(doSomething: GameModifierApplyScope<GameModel, Owner>.() -> Unit) {
        this.stateChecks.add(doSomething)
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

    fun executeStateCheck() {
        if (this.removeCondition?.invoke(this) == true) {
            meta.removeRule(this)
        }

        val context = createApplyContext()
        for (effect in this.stateChecks) {
            effect.invoke(context)
        }

        this.actionModifiers.forEach {
            meta.addAction(it.action, it.definition)
        }
        this.globalPreconditions.forEach(meta::addGlobalActionPrecondition)
    }

    private fun isActive(): Boolean = this.activeConditions.all { it.invoke(this) }

}

class GameModifierApplyContext<GameModel : Any, Owner>(
    override val ruleHolder: Owner,
    override val meta: GameMetaScope<GameModel>
) : GameModifierApplyScope<GameModel, Owner> {
    override val game: GameModel get() = meta.game
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