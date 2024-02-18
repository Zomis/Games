package net.zomis.games.context

import net.zomis.games.PlayerEliminationsWrite
import net.zomis.games.api.ConfigScope
import net.zomis.games.api.EventScope
import net.zomis.games.api.UsageScope
import net.zomis.games.api.ValueScope
import net.zomis.games.cards.CardZone
import net.zomis.games.components.IdGenerator
import net.zomis.games.components.resources.GameResource
import net.zomis.games.components.resources.MutableResourceMap
import net.zomis.games.dsl.*
import net.zomis.games.dsl.events.*
import net.zomis.games.dsl.flow.ActionDefinition
import net.zomis.games.dsl.flow.GameFlowActionScope
import net.zomis.games.dsl.flow.GameFlowScope
import net.zomis.games.dsl.flow.GameMetaScope
import net.zomis.games.dsl.impl.*
import net.zomis.games.rules.Rule
import net.zomis.games.rules.RuleSpec
import net.zomis.games.scorers.ScorerFactory
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

class ComponentDelegate<E>(initialValue: E): ReadWriteProperty<Entity?, E> {
    var value = initialValue
    override fun getValue(thisRef: Entity?, property: KProperty<*>): E = value
    override fun setValue(thisRef: Entity?, property: KProperty<*>, value: E) {
        println("Set $property on $thisRef to $value")
        this.value = value
    }
}

class ResourceMapDelegate(val map: MutableResourceMap, val resource: GameResource): ReadWriteProperty<Any?, Int> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): Int = map.getOrDefault(resource)
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) = map.set(resource, value)
}

class DynamicValueDelegate<E>(val function: () -> E): ReadOnlyProperty<Entity?, E> {
    override fun getValue(thisRef: Entity?, property: KProperty<*>): E = function.invoke()
}

interface HandlerScope<E, T>: UsageScope, ValueScope<E>, EventScope<T>, ConfigScope, net.zomis.games.api.ReplayableScope {
    // Do not mark with @GameMarker as it should be allowed to reference event from nested calls.
}

typealias ViewFunction = (ViewScope<Any>) -> Any?

class DelegateFactory<E, P: ReadOnlyProperty<Entity?, E>>(
    override val ctx: Context,
    val propertyFactory: (Context) -> P,
    val getter: (P) -> E,
    val setter: (P, E) -> Unit
): ContextHolder {
    var name: Any? = null
    var listView = false
    lateinit var newContext: Context
    lateinit var delegate: P
    internal val privateViews = mutableMapOf<Int, ViewFunction>()
    internal var publicView: ViewFunction? = {
        when {
            listView -> newContext.listView(it)
            newContext.children.isNotEmpty() -> newContext.mapView(it)
            else -> getter.invoke(delegate)
        }
    }

    operator fun provideDelegate(thisRef: Entity?, prop: KProperty<*>): P {
        this.name = this.name ?: prop.name
        this.newContext = ctx.createChild(thisRef, name!!, this)
        this.delegate = propertyFactory.invoke(newContext)
        return delegate
    }

    fun <T: Any> on(event: EventFactory<T>, priority: EventPriority, handler: HandlerScope<E, T>.() -> Unit): DelegateFactory<E, P> {
        this.ctx.onEvent<T, E>(event, priority, { handler.invoke(this); value }, { getter.invoke(delegate) }, { setter.invoke(delegate, it) })
        return this
    }

    fun <T: Any> on(event: EventFactory<T>, handler: HandlerScope<E, T>.() -> Unit): DelegateFactory<E, P>
        = this.on(event, EventPriority.NORMAL, handler)

    fun setup(init: GameStartScope<Any>.(E) -> E): DelegateFactory<E, P> {
        ctx.gameContext.onSetup.add {
            val result = init.invoke(this, getter.invoke(delegate))
            setter.invoke(delegate, result)
        }
        return this
    }
    fun onSetup(init: GameStartScope<Any>.(E) -> Unit): DelegateFactory<E, P> {
        ctx.gameContext.onSetup.add {
            init.invoke(this, getter.invoke(delegate))
        }
        return this
    }

    fun <T: Any> changeOn(event: EventFactory<T>, priority: EventPriority, handler: HandlerScope<E, T>.() -> E): DelegateFactory<E, P> {
        ctx.onEvent(event, priority, handler, { getter.invoke(delegate) }, { setter.invoke(delegate, it) })
        return this
    }
    fun <T: Any> changeOn(event: EventFactory<T>, handler: HandlerScope<E, T>.() -> E): DelegateFactory<E, P>
        = changeOn(event, EventPriority.NORMAL, handler)

    fun view(viewFunction: (ViewScope<Any>).(E) -> Any): DelegateFactory<E, P> {
        this.publicView = { viewFunction.invoke(it, getter.invoke(delegate)) }
        return this
    }
    fun privateView(playerIndices: List<Int>, view: (E) -> Any? = { it }): DelegateFactory<E, P> {
        playerIndices.forEach { index ->
            privateViews[index] = { view.invoke(getter.invoke(delegate)) }
        }
        return this
    }
    fun privateView(playerIndex: Int, view: (E) -> Any? = { it }): DelegateFactory<E, P> = this.privateView(listOf(playerIndex), view)
    fun publicView(view: (E) -> Any): DelegateFactory<E, P> {
        this.publicView = { view.invoke(getter.invoke(delegate)) }
        return this
    }

    fun hiddenView(): DelegateFactory<E, P> {
        this.publicView = { HiddenValue }
        return this
    }
}

fun <T : Entity> T.action(dsl: GameFlowActionScope<T, ChoiceAction>.() -> Unit) = ActionDelegate<T>(this, dsl)
class ChoiceAction
class ActionDelegate<T: Entity>(
    private val self: T,
    private val dsl: GameFlowActionScope<T, ChoiceAction>.() -> Unit,
) : PropertyDelegateProvider<T, ReadOnlyProperty<T, ActionDefinition<T, ChoiceAction>>> {
    override fun provideDelegate(
        thisRef: T,
        property: KProperty<*>
    ): ReadOnlyProperty<T, ActionDefinition<T, ChoiceAction>> {
        val name = property.name
        val actionType = GameActionCreator<T, ChoiceAction>(name, ChoiceAction::class, Map::class, serializer = {}, deserializer = { TODO() })
        val actionDefinition = ActionTypeDefinition(actionType, dsl)

        return ReadOnlyProperty<T, ActionDefinition<T, ChoiceAction>> { _, _ -> actionDefinition }
    }
}

class ActionFactory<T: Any, A: Any>(
    val name: String, val parameterType: KClass<A>,
    override val actionDsl: GameFlowActionScope<T, A>.() -> Unit
): ActionDefinition<T, A> {
    override var actionType = GameActionCreator<T, A>(name, parameterType, parameterType, { it }, { it as A })
}
class ActionTypeDefinition<GameModel: Any, A: Any>(
    override val actionType: ActionType<GameModel, A>,
    override val actionDsl: GameFlowActionScope<GameModel, A>.() -> Unit
) : ActionDefinition<GameModel, A>

class EventContextFactory<E: Any>(val ctx: Context): EventFactory<E> {
    override fun invoke(value: E, performEvent: (E) -> Unit) {
        ctx.rootContext().gameContext.events.fireEvent(this as EventFactory<Any>, value, performEvent as (Any) -> Unit)
    }
}

open class Entity(protected open val ctx: Context) {
    private fun <E> delegate(context: Context = ctx, factory: (Context) -> ComponentDelegate<E>): DelegateFactory<E, ComponentDelegate<E>> {
        return DelegateFactory(context, factory, { it.value }, { d, v -> d.value = v })
    }

    fun <E: Any> event(): EventFactory<E> = EventContextFactory(ctx)
    fun <E> playerComponent(function: ContextHolder.(Int) -> E): DelegateFactory<List<E>, ComponentDelegate<List<E>>> {
        fun listFactory(context: Context): ComponentDelegate<List<E>> {
            val list = ctx.playerIndices.map { index ->
                val playerContext by delegate(context) { ComponentDelegate(function.invoke(ContextHolderImpl(it), index)) }
                    .also { it.name = index }
                playerContext
            }
            return ComponentDelegate(list)
        }
        return delegate { listFactory(it) }.also { it.listView = true }
    }
    fun <E> component(function: ContextHolder.() -> E): DelegateFactory<E, ComponentDelegate<E>> {
        return delegate { ComponentDelegate(function.invoke(ContextHolderImpl(it))) }
    }
    fun resource(resourceMap: MutableResourceMap, resource: GameResource): ReadWriteProperty<Any?, Int> {
        return ResourceMapDelegate(resourceMap, resource) // TODO: Add possibility for this to have a view etc.
    }
    fun <T: Any> viewOnly(viewFunction: (ViewScope<T>).() -> Any) = dynamicValue {}.view { viewFunction.invoke(this as ViewScope<T>) }
    fun <E> value(function: ContextHolder.() -> E): DelegateFactory<E, ComponentDelegate<E>> = component(function)
    fun <E> dynamicValue(function: ContextHolder.() -> E): DelegateFactory<E, DynamicValueDelegate<E>> {
        val delegate = DynamicValueDelegate { function.invoke(ContextHolderImpl(ctx)) }
        return DelegateFactory(ctx, { delegate }, { it.function.invoke() }, {_, _ ->})
    }
    fun <T : Any, RuleHolder> rule(owner: RuleHolder, dsl: RuleSpec<T, RuleHolder>): RuleDelegateProvider<T, RuleHolder> = RuleDelegateProvider(ctx, owner, dsl)
    fun <T : Any, RuleHolder> ruleSpec(dsl: RuleSpec<T, RuleHolder>): RuleSpecDelegateProvider<T, RuleHolder> = RuleSpecDelegateProvider(ctx, dsl)
    fun playerReference(function: ContextHolder.() -> Int): DelegateFactory<Int, ComponentDelegate<Int>> = component(function)
    fun <E> cards(list: MutableList<E> = mutableListOf()): DelegateFactory<CardZone<E>, ComponentDelegate<CardZone<E>>> {
        val delegate = ComponentDelegate(CardZone(list))
        return this.delegate { delegate }.publicView { it.cards }
    }
    fun <T: Any, A: Any> action(name: String, parameter: KClass<A>, actionDefinition: GameFlowActionScope<T, A>.() -> Unit)
            = ActionFactory(name, parameter, actionDefinition)
    fun <T: Any, A: GameSerializable> actionSerializable(name: String, parameter: KClass<A>, actionDefinition: GameFlowActionScope<T, A>.() -> Unit)
            = ActionFactory(name, parameter, actionDefinition).also { it.actionType = it.actionType.serializer { a -> a.serialize() } }
    fun sharedIdGenerator() = ctx.gameContext.idGenerator
}
class GameContext(val meta: GameMetaScope<Any>, val events: EventsHandling<Any>, val playerCount: Int, val eliminations: PlayerEliminationsWrite, val configLookup: (GameConfig<Any>) -> Any) {
    val idGenerator = IdGenerator()
    internal val onSetup = mutableListOf<GameStartScope<Any>.() -> Unit>()
}
interface ContextHolder {
    val ctx: Context
    fun <E: Any> config(config: GameConfig<E>): E = ctx.gameContext.configLookup.invoke(config as GameConfig<Any>) as E
}
class ContextHolderImpl(override val ctx: Context): ContextHolder
class Context(val gameContext: GameContext, private val parent: Context?, val name: Any) {
    fun rootContext(): Context = parent?.rootContext() ?: this
    var view: ViewFunction? = { mapView(it) }
    fun view(viewScope: ViewScope<Any>): Any? = this.view?.invoke(viewScope)

    fun mapView(viewScope: ViewScope<Any>): Any = children.filter { it.view != null }
        .associate { it.name to it.view(viewScope) }.filterValues { it != HiddenValue }
    fun listView(viewScope: ViewScope<Any>): Any = children.map { it.view(viewScope) }

    fun <T: Any, E> onEvent(event: EventFactory<T>, priority: EventPriority, handler: HandlerScope<E, T>.() -> E, getter: () -> E, setter: (E) -> Unit) {
        this.rootContext().gameContext.events.addEventListener(priority,
            EventListenerContext(gameContext, event as EventFactory<Any>, handler as HandlerScope<Any, Any>.() -> Any,
                { getter.invoke() as Any }, { setter.invoke(it as E) })
        )
    }

    fun <E, P: ReadOnlyProperty<Entity?, E>> createChild(thisRef: Entity?, name: Any, factory: DelegateFactory<E, P>): Context {
        val newContext = Context(gameContext, this, name)
        newContext.view = {
            val privateView = factory.privateViews[it.viewer] ?: factory.publicView
            privateView?.invoke(it) ?: HiddenValue
        }
        this.children.add(newContext)
        return newContext
    }

    val playerIndices get() = (0 until gameContext.playerCount)
    internal val children = mutableListOf<Context>()
}
class EventListenerContext(
    val gameContext: GameContext,
    private val eventFactory: EventFactory<Any>,
    private val myHandler: HandlerScope<Any, Any>.() -> Any,
    val getter: () -> Any,
    private val resultHandler: (Any) -> Unit
): EventListener {
    override fun execute(scope: GameEventEffectScope<Any, Any>) {
        if (this.eventFactory != scope.effectSource) return
        val event = scope.event

        val result = myHandler.invoke(object : HandlerScope<Any, Any> {
            override val value: Any get() = getter.invoke()
            override val event: Any get() = event
            override fun <C : Any> config(config: GameConfig<C>): C = gameContext.configLookup.invoke(config as GameConfig<Any>) as C
            override val replayable: ReplayStateI get() = scope.meta.replayable
        })
        resultHandler.invoke(result)
    }

}
class GameCreatorContext<T: ContextHolder>(val gameType: String, val function: GameCreatorContextScope<T>.() -> Unit): GameCreatorContextScope<T> {
    private var playerRange = 0..0
    private lateinit var init: ContextHolder.() -> T
    private var gameFlow: suspend GameFlowScope<T>.() -> Unit = {
        loop {
            step("no-op step") {}
        }
    }
    private val configs = mutableListOf<GameConfig<Any>>()
    private val context = GameDslContext<T>(gameType)

    override fun players(players: IntRange) {
        this.playerRange = players
    }

    override fun init(function: ContextHolder.() -> T) {
        this.init = function
    }

    override fun gameFlow(function: suspend GameFlowScope<T>.() -> Unit) {
        this.gameFlow = function
    }

    override fun <E: Any> config(key: String, default: () -> E): GameConfig<E> {
        val config = GameConfigImpl(key, default)
        this.configs.add(config as GameConfig<Any>)
        return config
    }

    override val scorers: ScorerFactory<T> = context.scorers
    override fun ai(name: String, block: GameAIScope<T>.() -> Unit): GameAI<T> = context.ai(name, block)

    fun toGameSpec(): GameSpec<T> {
        this.function.invoke(this)
        val dsl: GameDslScope<T>.() -> Unit = {
            for (config in this@GameCreatorContext.configs) {
                this.config(config.key, config.default)
            }
            setup {
                players(this@GameCreatorContext.playerRange)
                onStart {
                    this.game.ctx.gameContext.onSetup.forEach { it.invoke(this as GameStartScope<Any>) }
                }
                init {
                    val gc = GameContext(this.meta as GameMetaScope<Any>, this.events as EventsHandling<Any>, this.playerCount, this.eliminationCallback) { config(it) }
                    val context = Context(gc, null, "")
                    context.view = {
                        context.children.associate { it.name to it.view }
                    }
                    this@GameCreatorContext.init.invoke(ContextHolderImpl(context))
                }
            }
            gameFlow {
                this@GameCreatorContext.gameFlow.invoke(this)
            }
            gameFlowRules {
                beforeReturnRule("view") {
                    game.ctx.children.filter { it.view != null }.forEach {
                        view(it.name.toString()) { it.view(this as ViewScope<Any>) }
                    }
                }
            }
            val oldContext = this@GameCreatorContext.context
            val newContext = this as GameDslContext<T>
            newContext.copyAIsFrom(oldContext)
        }
        return GameSpec(gameType, dsl)
    }
}
@GameMarker
interface GameCreatorContextScope<T: Any>: UsageScope {
    fun players(players: IntRange)
    fun init(function: ContextHolder.() -> T)
    fun gameFlow(function: suspend GameFlowScope<T>.() -> Unit)
    fun <E : Any> config(key: String, default: () -> E): GameConfig<E>
    fun ai(name: String, block: GameAIScope<T>.() -> Unit): GameAI<T>
    val scorers: ScorerFactory<T>
}
