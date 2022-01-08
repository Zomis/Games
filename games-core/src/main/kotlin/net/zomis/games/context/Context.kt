package net.zomis.games.context

import net.zomis.games.PlayerEliminationsWrite
import net.zomis.games.cards.CardZone
import net.zomis.games.dsl.*
import net.zomis.games.dsl.flow.ActionDefinition
import net.zomis.games.dsl.flow.GameFlowActionScope
import net.zomis.games.dsl.flow.GameFlowScope
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

class ComponentDelegate<E>(initialValue: E): ReadWriteProperty<Entity?, E> {
    var value = initialValue
    override fun getValue(thisRef: Entity?, property: KProperty<*>): E = value
    override fun setValue(thisRef: Entity?, property: KProperty<*>, value: E) {
        this.value = value
    }
}
interface HandlerScope<E> {
    val replayable: ReplayableScope
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

    fun <T: Any> on(event: Event<T>, handler: HandlerScope<E>.(T) -> Unit): ContextFactory<E> {
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
    override val actionType = GameActionCreator<T, A, A>(name, parameterType, parameterType, { it }, { it })
    operator fun provideDelegate(thisRef: Entity?, prop: KProperty<*>): ReadWriteProperty<Entity?, ActionDefinition<T, A>> {
        return ComponentDelegate(this@ActionFactory)
    }
}

class Event<E: Any>(ctx: Context) {
    operator fun invoke(e: E) {
//        TODO("event listeners not implemented yet")
    }
}

open class Entity(protected open val ctx: Context) {
    fun <E: Any> event(clazz: KClass<E>): ContextFactory<Event<E>> = ContextFactory<Event<E>>(ctx) { Event(ctx) }.hiddenView()
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
    fun playerReference(function: ContextHolder.() -> Int): ContextFactory<Int> = ContextFactory(ctx, function)
    fun <E> cards(): ContextFactory<CardZone<E>> = ContextFactory<CardZone<E>>(ctx) { CardZone() }.publicView { it.cards }
    fun <T: Any, A: Any> action(name: String, parameter: KClass<A>, actionDefinition: GameFlowActionScope<T, A>.() -> Unit)
        = ActionFactory(name, parameter, actionDefinition)
}
class GameContext(val playerCount: Int, val eliminations: PlayerEliminationsWrite)
interface ContextHolder {
    val ctx: Context
}
class Context(val gameContext: GameContext, val parent: Context?, val name: Any) {
    val rootContext: Context get() = parent?.rootContext ?: this
    var view: ((ViewScope<Any>) -> Any?)? = { mapView(it) }
    fun view(viewScope: ViewScope<Any>): Any? = this.view?.invoke(viewScope)

    fun mapView(viewScope: ViewScope<Any>): Any = children.filter { it.view != null }.associate { it.name to it.view(viewScope) }
    fun listView(viewScope: ViewScope<Any>): Any = children.map { it.view(viewScope) }

    val playerIndices get() = (0 until gameContext.playerCount)
    val children = mutableListOf<Context>()

    internal val onSetup = mutableListOf<GameStartScope<Any>.() -> Unit>()

}
class GameCreatorContext<T: ContextHolder>(val gameName: String, val function: GameCreatorContextScope<T>.() -> Unit): GameCreatorContextScope<T> {
    private var playerRange = 0..0
    private lateinit var init: ContextHolder.() -> T
    private lateinit var gameFlow: suspend GameFlowScope<T>.() -> Unit

    override fun players(players: IntRange) {
        this.playerRange = players
    }

    override fun init(function: ContextHolder.() -> T) {
        this.init = function
    }

    override fun gameFlow(function: suspend GameFlowScope<T>.() -> Unit) {
        this.gameFlow = function
    }

    fun toDsl(): GameDsl<T>.() -> Unit {
        this.function.invoke(this)
        return {
            setup {
                players(playerRange)
                onStart {
                    this.game.ctx.onSetup.forEach { it.invoke(this as GameStartScope<Any>) }
                }
                init {
                    val gc = GameContext(this.playerCount, this.eliminationCallback)
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
}
