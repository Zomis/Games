package net.zomis.games.dsl.impl

import net.zomis.games.dsl.*
import kotlin.reflect.KClass

class GameModelContext<T, C> : GameModel<T, C> {
    lateinit var factory: (C?) -> T
    lateinit var config: () -> C

    override fun defaultConfig(creator: () -> C) {
        this.config = creator
    }

    override fun init(factory: (C?) -> T) {
        this.factory = factory
    }
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

class GameLogicActionTypeSimple<T : Any, P : Any>(override val actionType: String, private val model: T,
        private val options: Iterable<P>,
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
        return options.mapNotNull {option ->
            val action = Action(model, playerIndex, actionType, option)
            val allowed = this.allowedCheck(action)
            return@mapNotNull action.takeIf { allowed }
        }
    }
}

interface GameLogicActionType<T : Any, A : Any> {
    val actionType: String
    fun availableActions(playerIndex: Int): Iterable<Actionable<T, A>>
    fun actionAllowed(action: Actionable<T, A>): Boolean
    fun performAction(action: Actionable<T, A>)
    fun createAction(playerIndex: Int, parameter: A): Actionable<T, A>
}

class GameLogicContext<T : Any>(private val model: T, private val replayState: ReplayState) : GameLogic<T> {
    val actions = mutableMapOf<ActionType<*>, GameLogicActionType<T, *>>()
    var winner: (T) -> PlayerIndex = { null }

    override fun <P : Any> action2D(actionType: ActionType<Point>, grid: GridDsl<T, P>, logic: ActionLogic2D<T, P>) {
        val context = GameLogicActionType2D(actionType.name, model, grid, replayState)
        logic(context)
        actions[actionType] = context
    }

    override fun simpleAction(actionType: ActionType<Unit>, logic: ActionLogicSimple<T>) {
        val context = GameLogicActionTypeUnit(actionType.name, model, replayState)
        logic(context)
        actions[actionType] = context
    }

    override fun intAction(actionType: ActionType<Int>, options: Iterable<Int>, logic: ActionLogicInt<T>) {
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

class GameViewContext2D<T, P>(override val model: T) : GameView2D<T, P> {

    private var ownerFunction: ((tile: P) -> Int?)? = null
    private val properties = mutableListOf<Pair<String, (P) -> Any?>>()

    override fun owner(function: (tile: P) -> Int?) {
        this.ownerFunction = function
    }

    override fun property(name: String, value: (tile: P) -> Any?) {
        this.properties.add(name to value)
    }

    fun view(p: P): Map<String, Any?> {
        val tileMap = mutableMapOf<String, Any?>()
        for (property in properties) {
            tileMap[property.first] = property.second(p)
        }
        if (this.ownerFunction != null) {
            tileMap["owner"] = this.ownerFunction!!(p)
        }
        return tileMap
    }

}

class GameGridBuilder<T : Any, P>(override val model: T) : GameGrid<T, P>, GridSpec<T, P> {
    override lateinit var sizeX: (T) -> Int
    override lateinit var sizeY: (T) -> Int
    lateinit var getter: (x: Int, y: Int) -> P

    override fun get(model: T, x: Int, y: Int): P {
        return this.getter(x, y)
    }

    override fun size(sizeX: Int, sizeY: Int) {
        this.sizeX = { sizeX }
        this.sizeY = { sizeY }
    }

    override fun getter(getter: (x: Int, y: Int) -> P) {
        this.getter = getter
    }

}

class GameViewContext<T : Any>(val model: T, override val viewer: PlayerIndex, private val replayState: ReplayState) : GameView<T> {
    private val viewResult: MutableMap<String, Any?> = mutableMapOf()

    override fun result(): Map<String, Any?> {
        return viewResult.toMap()
    }

    override fun value(key: String, value: (T) -> Any?) {
        this.viewResult[key] = value(model)
    }

    override fun state(key: String, function: ReplayScope.(T) -> Any?) {
        this.viewResult[key] = function(replayState, model)
    }

    override fun currentPlayer(function: (T) -> Int) {
        viewResult["currentPlayer"] = function(model)
    }

    override fun <P> grid(name: String, grid: GridDsl<T, P>, view: ViewDsl2D<T, P>) {
        val gridSpec = GameGridBuilder<T, P>(model)
        gridSpec.apply(grid as GameGridBuilder<T, P>.() -> Unit)
        val context = GameViewContext2D<T, P>(model)
        view(context)
        viewResult[name] = (0 until gridSpec.sizeY(model)).map {y ->
            (0 until gridSpec.sizeX(model)).map {x ->
                val p = gridSpec.get(model, x, y)
                context.view(p)
            }
        }
    }

    override fun winner(function: (T) -> Int?) {
        viewResult["winner"] = function(model)
    }
}

class ReplayState: EffectScope, ReplayScope {
    private val currentAction = mutableMapOf<String, Any>()
    private val mostRecent = mutableMapOf<String, Any>()

    override fun state(key: String, value: Any) {
        mostRecent[key] = value
        currentAction[key] = value
    }

    override fun fullState(key: String): Any? = mostRecent[key]

    override fun state(key: String): Any = currentAction[key] ?: throw IllegalStateException("State '$key' not found")
}

class GameDslContext<T : Any> : GameDsl<T> {
    override fun <P> gridSpec(spec: GameGrid<T, P>.() -> Unit): GridDsl<T, P> {
        return spec
    }

    lateinit var configClass: KClass<*>
    lateinit var modelDsl: GameModelDsl<T, Any>
    lateinit var logicDsl: GameLogicDsl<T>
    lateinit var viewDsl: GameViewDsl<T>

    val model = GameModelContext<T, Any>()

    override fun <C : Any> setup(configClass: KClass<C>, modelDsl: GameModelDsl<T, C>) {
        this.configClass = configClass
        this.modelDsl = modelDsl as GameModelDsl<T, Any>
    }

    override fun logic(logicDsl: GameLogicDsl<T>) {
        this.logicDsl = logicDsl
    }
    override fun view(viewDsl: GameViewDsl<T>) {
        this.viewDsl = viewDsl
    }
}
