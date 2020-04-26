package net.zomis.games.dsl.impl

import net.zomis.games.PlayerEliminations
import net.zomis.games.dsl.*
import kotlin.reflect.KClass

class GameModelContext<T, C> : GameModel<T, C> {
    var playerCount: IntRange = 2..2
    lateinit var factory: GameFactoryScope<C>.(C?) -> T
    lateinit var config: () -> C
    var onStart: ReplayableScope.(T) -> Unit = {}

    override fun players(playerCount: IntRange) {
        this.playerCount = playerCount
    }

    override fun defaultConfig(creator: () -> C) {
        this.config = creator
    }

    override fun init(factory: GameFactoryScope<C>.(C?) -> T) {
        this.factory = factory
    }

    override fun onStart(effect: ReplayableScope.(T) -> Unit) {
        this.onStart = effect
    }
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

class GameViewContext<T : Any>(val model: T, private val eliminations: PlayerEliminations,
       override val viewer: PlayerIndex, private val replayState: ReplayState) : GameView<T> {
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

    override fun eliminations() {
        val eliminationsDone = eliminations.eliminations()
        viewResult["eliminations"] = (0 until eliminations.playerCount).map {playerIndex ->
            val elimination = eliminationsDone.firstOrNull { it.playerIndex == playerIndex }
            if (elimination == null) {
                mapOf("eliminated" to false)
            } else {
                mapOf("eliminated" to true, "result" to elimination.winResult, "position" to elimination.position)
            }
        }
    }
}

class ReplayState(override val playerEliminations: PlayerEliminations): EffectScope, ReplayScope, ReplayableScope {
    private val currentAction = mutableMapOf<String, Any>()
    private val mostRecent = mutableMapOf<String, Any>()

    override fun replayable(): ReplayableScope {
        return this
    }

    fun setReplayState(state: Map<String, Any>?) {
        currentAction.clear()
        if (state != null) {
            currentAction.putAll(state)
        }
    }

    private fun <T: Any> replayable(key: String, default: () -> T): T {
        if (currentAction.containsKey(key)) {
            return currentAction[key] as T!!
        }
        val value = default()
        mostRecent[key] = value
        currentAction[key] = value
        return value
    }

    override fun map(key: String, default: () -> Map<String, Any>): Map<String, Any> = replayable(key, default)
    override fun int(key: String, default: () -> Int): Int = replayable(key, default)
    override fun ints(key: String, default: () -> List<Int>): List<Int> = replayable(key, default)
    override fun string(key: String, default: () -> String): String = replayable(key, default)
    override fun strings(key: String, default: () -> List<String>): List<String> = replayable(key, default)
    override fun list(key: String, default: () -> List<Map<String, Any>>): List<Map<String, Any>> = replayable(key, default)

    override fun state(key: String, value: Any) {
        mostRecent[key] = value
        currentAction[key] = value
    }

    override fun fullState(key: String): Any? = mostRecent[key]

    override fun state(key: String): Any = currentAction[key] ?: throw IllegalStateException("State '$key' not found")

    fun lastMoveState(): Map<String, Any?> = currentAction.toMap()
    fun resetLastMove() {
        currentAction.clear()
    }
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

    override fun setup(modelDsl: GameModelDsl<T, Unit>) {
        return this.setup(Unit::class, modelDsl)
    }

    override fun logic(logicDsl: GameLogicDsl<T>) {
        this.logicDsl = logicDsl
    }
    override fun view(viewDsl: GameViewDsl<T>) {
        this.viewDsl = viewDsl
    }
}
