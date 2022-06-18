package net.zomis.games.context

import net.zomis.games.PlayerEliminationsWrite
import net.zomis.games.cards.CardZone
import net.zomis.games.dsl.*
import net.zomis.games.dsl.flow.ActionDefinition
import net.zomis.games.dsl.flow.GameFlowActionScope
import net.zomis.games.dsl.flow.GameFlowScope
import net.zomis.games.dsl.impl.GameConfigImpl
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

object HiddenValue
enum class EventPriority {
    EARLIEST,
    EARLIER,
    EARLY,
    NORMAL,
    LATE,
    LATER,
    LATEST
}
class ComponentDelegate<E>(initialValue: E): ReadWriteProperty<Entity?, E> {
    var value = initialValue
    override fun getValue(thisRef: Entity?, property: KProperty<*>): E = value
    override fun setValue(thisRef: Entity?, property: KProperty<*>, value: E) {
        println("Set $property on $thisRef to $value")
        this.value = value
    }
}

class DynamicValueDelegate<E>(val function: () -> E): ReadOnlyProperty<Entity?, E> {
    override fun getValue(thisRef: Entity?, property: KProperty<*>): E = function.invoke()
}

interface HandlerScope<E, T> {
    val replayable: ReplayableScope
    val value: E
    val event: T
    fun <E: Any> config(config: GameConfig<E>): E
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

    fun <T: Any> on(event: Event<T>, priority: EventPriority, handler: HandlerScope<E, T>.() -> Unit): DelegateFactory<E, P> {
        this.ctx.onEvent<T, E>(event, priority, { handler.invoke(this); value }, { getter.invoke(delegate) }, { setter.invoke(delegate, it) })
        return this
    }

    fun <T: Any> on(event: Event<T>, handler: HandlerScope<E, T>.() -> Unit): DelegateFactory<E, P>
        = this.on(event, EventPriority.NORMAL, handler)

    fun setup(init: GameStartScope<Any>.(E) -> E): DelegateFactory<E, P> {
        ctx.gameContext.onSetup.add {
            val result = init.invoke(this, getter.invoke(delegate))
            setter.invoke(delegate, result)
        }
        return this
    }

    fun <T: Any> changeOn(event: Event<T>, priority: EventPriority, handler: HandlerScope<E, T>.() -> E): DelegateFactory<E, P> {
        ctx.onEvent(event, priority, handler, { getter.invoke(delegate) }, { setter.invoke(delegate, it) })
        return this
    }
    fun <T: Any> changeOn(event: Event<T>, handler: HandlerScope<E, T>.() -> E): DelegateFactory<E, P>
        = changeOn(event, EventPriority.NORMAL, handler)

    fun privateView(playerIndices: List<Int>, view: (E) -> Any): DelegateFactory<E, P> {
        playerIndices.forEach { index ->
            privateViews[index] = { view.invoke(getter.invoke(delegate)) }
        }
        return this
    }
    fun privateView(playerIndex: Int, view: (E) -> Any): DelegateFactory<E, P> = this.privateView(listOf(playerIndex), view)
    fun publicView(view: (E) -> Any): DelegateFactory<E, P> {
        this.publicView = { view.invoke(getter.invoke(delegate)) }
        return this
    }

    fun hiddenView(): DelegateFactory<E, P> {
        this.publicView = { HiddenValue }
        return this
    }
}

class ActionFactory<T: Any, A: Any>(
    val name: String, val parameterType: KClass<A>,
    override val actionDsl: GameFlowActionScope<T, A>.() -> Unit
): ActionDefinition<T, A> {
    override var actionType = GameActionCreator<T, A>(name, parameterType, parameterType, { it }, { it as A })
}

class Event<E: Any>(val ctx: Context) {
    operator fun invoke(c: EventTools, e: E) {
        ctx.rootContext().gameContext.fireEvent(c, this as Event<Any>, e)
    }
}

open class Entity(protected open val ctx: Context) {
    private fun <E> delegate(context: Context = ctx, factory: (Context) -> ComponentDelegate<E>): DelegateFactory<E, ComponentDelegate<E>> {
        return DelegateFactory(context, factory, { it.value }, { d, v -> d.value = v })
    }

    fun <E: Any> event(): Event<E> = Event(ctx)
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
    fun <E> value(function: ContextHolder.() -> E): DelegateFactory<E, ComponentDelegate<E>> = component(function)
    fun <E> dynamicValue(function: ContextHolder.() -> E): DelegateFactory<E, DynamicValueDelegate<E>> {
        val delegate = DynamicValueDelegate { function.invoke(ContextHolderImpl(ctx)) }
        return DelegateFactory(ctx, { delegate }, { it.function.invoke() }, {_, _ ->})
    }
    fun playerReference(function: ContextHolder.() -> Int): DelegateFactory<Int, ComponentDelegate<Int>> = component(function)
    fun <E> cards(list: MutableList<E> = mutableListOf()): DelegateFactory<CardZone<E>, ComponentDelegate<CardZone<E>>> {
        val delegate = ComponentDelegate(CardZone(list))
        return this.delegate { delegate }.publicView { it.cards }
    }
    fun <T: Any, A: Any> action(name: String, parameter: KClass<A>, actionDefinition: GameFlowActionScope<T, A>.() -> Unit)
            = ActionFactory(name, parameter, actionDefinition)
    fun <T: Any, A: GameSerializable> actionSerializable(name: String, parameter: KClass<A>, actionDefinition: GameFlowActionScope<T, A>.() -> Unit)
            = ActionFactory(name, parameter, actionDefinition).also { it.actionType = it.actionType.serializer { a -> a.serialize() } }
}
class GameContext(val playerCount: Int, val eliminations: PlayerEliminationsWrite, val configLookup: (GameConfig<Any>) -> Any) {
    internal fun fireEvent(c: EventTools, event: Event<Any>, eventValue: Any) {
        for (priority in EventPriority.values()) {
            onEvent[priority]?.forEach {
                it.fire(c, event, eventValue)
            }
        }
    }
    internal fun addEventListener(priority: EventPriority, listener: EventListener) {
        onEvent.getOrPut(priority) { mutableListOf() }.add(listener)
    }

    private val onEvent = mutableMapOf<EventPriority, MutableList<EventListener>>()
    internal val onSetup = mutableListOf<GameStartScope<Any>.() -> Unit>()
}
interface ContextHolder {
    val ctx: Context
}
class ContextHolderImpl(override val ctx: Context): ContextHolder
class Context(val gameContext: GameContext, private val parent: Context?, val name: Any) {
    fun rootContext(): Context = parent?.rootContext() ?: this
    var view: ViewFunction? = { mapView(it) }
    fun view(viewScope: ViewScope<Any>): Any? = this.view?.invoke(viewScope)

    fun mapView(viewScope: ViewScope<Any>): Any = children.filter { it.view != null }
        .associate { it.name to it.view(viewScope) }.filterValues { it != HiddenValue }
    fun listView(viewScope: ViewScope<Any>): Any = children.map { it.view(viewScope) }

    fun <T: Any, E> onEvent(event: Event<T>, priority: EventPriority, handler: HandlerScope<E, T>.() -> E, getter: () -> E, setter: (E) -> Unit) {
        this.rootContext().gameContext.addEventListener(priority,
            EventListener(gameContext, event as Event<Any>, handler as HandlerScope<Any, Any>.() -> Any,
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
class EventListener(
    val gameContext: GameContext,
    val event: Event<Any>,
    val handler: HandlerScope<Any, Any>.() -> Any,
    val getter: () -> Any,
    val resultHandler: (Any) -> Unit
) {
    fun fire(c: EventTools, event: Event<Any>, eventValue: Any) {
        if (this.event == event) {
            val value = getter.invoke()
            val result = this.handler.invoke(object : HandlerScope<Any, Any> {
                override val replayable: ReplayableScope get() = c.replayable
                override val value: Any get() = value
                override val event: Any get() = eventValue
                override fun <E : Any> config(config: GameConfig<E>): E {
                    return gameContext.configLookup.invoke(config as GameConfig<Any>) as E
                }
            })
            resultHandler.invoke(result)
        }
    }
}
class GameCreatorContext<T: ContextHolder>(val gameName: String, val function: GameCreatorContextScope<T>.() -> Unit): GameCreatorContextScope<T> {
    private var playerRange = 0..0
    private lateinit var init: ContextHolder.() -> T
    private lateinit var gameFlow: suspend GameFlowScope<T>.() -> Unit
    private val configs = mutableListOf<GameConfig<Any>>()

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

    fun toDsl(): GameDsl<T>.() -> Unit {
        this.function.invoke(this)
        return {
            for (config in configs) {
                this.config(config.key, config.default)
            }
            setup {
                players(playerRange)
                onStart {
                    this.game.ctx.gameContext.onSetup.forEach { it.invoke(this as GameStartScope<Any>) }
                }
                init {
                    val gc = GameContext(this.playerCount, this.eliminationCallback) { config(it) }
                    val context = Context(gc, null, "")
                    context.view = {
                        context.children.associate { it.name to it.view }
                    }
                    init.invoke(ContextHolderImpl(context))
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
        }
    }
}
interface GameCreatorContextScope<T: Any> {
    fun players(players: IntRange)
    fun init(function: ContextHolder.() -> T)
    fun gameFlow(function: suspend GameFlowScope<T>.() -> Unit)
    fun <E : Any> config(key: String, default: () -> E): GameConfig<E>
}
