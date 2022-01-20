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

class DynamicValueDelegateFactory<E>(override var ctx: Context, val function: ContextHolder.() -> E): ContextHolder {
    val value get() = function.invoke(this)

    private val privateViews = mutableMapOf<Int, (ViewScope<Any>) -> Any?>()
    var view: ((ViewScope<Any>) -> Any?)? = {
        function.invoke(this)
    }

    operator fun provideDelegate(thisRef: Entity?, prop: KProperty<*>): ReadOnlyProperty<Entity?, E> {
        val newContext = Context(ctx.gameContext, ctx, prop.name)
        newContext.view = {
            val privateView = privateViews[it.viewer] ?: view
            privateView?.invoke(it)
        }
        ctx.children.add(newContext)
        this.ctx = newContext
        return DynamicValueDelegate { function.invoke(this) }
    }

    fun privateView(playerIndices: List<Int>, view: (E) -> Any): DynamicValueDelegateFactory<E> {
        playerIndices.forEach { index ->
            privateViews[index] = { view.invoke(value) }
        }
        return this
    }
    fun privateView(playerIndex: Int, view: (E) -> Any): DynamicValueDelegateFactory<E> = this.privateView(listOf(playerIndex), view)
    fun publicView(view: (E) -> Any): DynamicValueDelegateFactory<E> {
        this.view = { view.invoke(value) }
        return this
    }

    fun hiddenView(): DynamicValueDelegateFactory<E> {
        this.view = null
        return this
    }
}

class ContextFactory<E>(var ctx: Context, val default: ContextHolder.() -> E) {
    var name: Any? = null
    private lateinit var delegate: ComponentDelegate<E>
    private val privateViews = mutableMapOf<Int, (ViewScope<Any>) -> Any?>()

    var listView = false
    var view: ((ViewScope<Any>) -> Any?)? = {
        when {
            listView -> ctx.listView(it)
            ctx.children.isNotEmpty() -> ctx.mapView(it)
            else -> delegate.value
        }
    }
/*
* Add event listeners, setup listeners
* Set view (private, public, hidden) -- name doesn't come until "provide delegate"
* Set value, setters and getters, dynamic value...
*
*/
    operator fun provideDelegate(thisRef: Entity?, prop: KProperty<*>): ReadWriteProperty<Entity?, E> {
        val newName = name ?: prop.name
        val newContext = Context(ctx.gameContext, ctx, newName)
        this.delegate = ComponentDelegate(default.invoke(object : ContextHolder {
            override val ctx: Context get() = newContext
        }))
        newContext.view = {
            val privateView = privateViews[it.viewer] ?: view
            privateView?.invoke(it)
        }
        ctx.children.add(newContext)
        this.ctx = newContext
        return delegate
    }

    fun setup(init: GameStartScope<Any>.(E) -> E): ContextFactory<E> {
        ctx.rootContext.onSetup.add {
            val result = init.invoke(this, delegate.value)
            delegate.value = result
        }
        return this
    }

    fun <T: Any> changeOn(event: Event<T>, handler: HandlerScope<E, T>.() -> E): ContextFactory<E> {
        ctx.onEvent(event, handler) { delegate }
        return this
    }
    fun <T: Any> on(event: Event<T>, handler: HandlerScope<E, T>.() -> Unit): ContextFactory<E> {
        ctx.onEvent(event, { handler.invoke(this); value }) { delegate }
        return this
    }

    fun privateView(playerIndices: List<Int>, view: (E) -> Any): ContextFactory<E> {
        playerIndices.forEach { index ->
            privateViews.put(index) {
                view.invoke(delegate.value)
            }
        }
        return this
    }
    fun privateView(playerIndex: Int, view: (E) -> Any): ContextFactory<E> = this.privateView(listOf(playerIndex), view)
    fun publicView(view: (E) -> Any): ContextFactory<E> {
        this.view = { view.invoke(delegate.value) }
        return this
    }

    fun hiddenView(): ContextFactory<E> {
        this.view = null
        return this
    }
}

class ActionFactory<T: Any, A: Any>(
    val name: String, val parameterType: KClass<A>,
    override val actionDsl: GameFlowActionScope<T, A>.() -> Unit
): ActionDefinition<T, A> {
    override var actionType = GameActionCreator<T, A>(name, parameterType, parameterType, { it }, { it as A })
    operator fun provideDelegate(thisRef: Entity?, prop: KProperty<*>): ReadWriteProperty<Entity?, ActionDefinition<T, A>> {
        return ComponentDelegate(this@ActionFactory)
    }
}

class Event<E: Any>(val ctx: Context) {
    operator fun invoke(c: EventTools, e: E) {
        ctx.rootContext.onEvent.forEach {
            it.fire(c, this as Event<Any>, e)
        }
    }
}

open class Entity(protected open val ctx: Context) {
    // TODO: Don't think `action` and `event` needs to be delegates.
    fun <E: Any> event(): ContextFactory<Event<E>> = ContextFactory<Event<E>>(ctx) { Event(ctx) }.hiddenView()
    fun <E: Any> event(clazz: KClass<E>) = event<E>()
    fun <E> playerComponent(function: ContextHolder.(Int) -> E): ContextFactory<List<E>> {
        return ContextFactory(ctx) {
            val players = ctx.playerIndices.map { index ->
                val playerContext by ContextFactory<E>(this.ctx) { function.invoke(this, index) }.also { it.name = index }
                playerContext
            }
            players
        }.also { it.listView = true }
    }
    fun <E> component(function: ContextHolder.() -> E): ContextFactory<E> = ContextFactory(ctx, function)
    fun <E> value(function: ContextHolder.() -> E): ContextFactory<E> = component(function)
    fun <E> dynamicValue(function: ContextHolder.() -> E): DynamicValueDelegateFactory<E> = DynamicValueDelegateFactory(ctx, function)
    fun playerReference(function: ContextHolder.() -> Int): ContextFactory<Int> = ContextFactory(ctx, function)
    fun <E> cards(list: MutableList<E> = mutableListOf()): ContextFactory<CardZone<E>> = ContextFactory(ctx) { CardZone(list) }.publicView { it.cards }
    fun <T: Any, A: Any> action(name: String, parameter: KClass<A>, actionDefinition: GameFlowActionScope<T, A>.() -> Unit)
            = ActionFactory(name, parameter, actionDefinition)
    fun <T: Any, A: GameSerializable> actionSerializable(name: String, parameter: KClass<A>, actionDefinition: GameFlowActionScope<T, A>.() -> Unit)
            = ActionFactory(name, parameter, actionDefinition).also { it.actionType = it.actionType.serializer { a -> a.serialize() } }
}
class GameContext(val playerCount: Int, val eliminations: PlayerEliminationsWrite, val configLookup: (GameConfig<Any>) -> Any)
interface ContextHolder {
    val ctx: Context
}
class Context(val gameContext: GameContext, val parent: Context?, val name: Any) {
    val rootContext: Context get() = parent?.rootContext ?: this
    var view: ((ViewScope<Any>) -> Any?)? = { mapView(it) }
    fun view(viewScope: ViewScope<Any>): Any? = this.view?.invoke(viewScope)

    fun mapView(viewScope: ViewScope<Any>): Any = children.filter { it.view != null }.associate { it.name to it.view(viewScope) }
    fun listView(viewScope: ViewScope<Any>): Any = children.map { it.view(viewScope) }
    fun <T: Any, E> onEvent(event: Event<T>, handler: HandlerScope<E, T>.() -> E, value: () -> ComponentDelegate<E>) {
        this.rootContext.onEvent.add(
            EventListener(gameContext, event as Event<Any>, handler as HandlerScope<Any, Any>.() -> Any) {
                value.invoke() as ComponentDelegate<Any>
            }
        )
    }

    val playerIndices get() = (0 until gameContext.playerCount)
    val children = mutableListOf<Context>()

    internal val onEvent = mutableListOf<EventListener>()
    internal val onSetup = mutableListOf<GameStartScope<Any>.() -> Unit>()

}
class EventListener(
    val gameContext: GameContext,
    val event: Event<Any>,
    val handler: HandlerScope<Any, Any>.() -> Any,
    val delegate: () -> ComponentDelegate<Any>
) {
    fun fire(c: EventTools, event: Event<Any>, eventValue: Any) {
        if (this.event == event) {
            var delegateValue by delegate.invoke()
            val result = this.handler.invoke(object : HandlerScope<Any, Any> {
                override val replayable: ReplayableScope get() = c.replayable
                override val value: Any get() = delegateValue
                override val event: Any get() = eventValue
                override fun <E : Any> config(config: GameConfig<E>): E {
                    return gameContext.configLookup.invoke(config as GameConfig<Any>) as E
                }
            })
            delegateValue = result
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
                    this.game.ctx.onSetup.forEach { it.invoke(this as GameStartScope<Any>) }
                }
                init {
                    val gc = GameContext(this.playerCount, this.eliminationCallback) { config(it) }
                    val context = Context(gc, null, "")
                    context.view = {
                        context.children.associate { it.name to it.view }
                    }
                    init.invoke(object : ContextHolder {
                        override val ctx: Context get() = context
                    })
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
