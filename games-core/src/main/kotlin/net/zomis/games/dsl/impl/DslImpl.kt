package net.zomis.games.dsl.impl

import net.zomis.games.PlayerEliminationsWrite
import net.zomis.games.common.PlayerIndex
import net.zomis.games.dsl.*
import net.zomis.games.dsl.flow.GameFlowImpl
import kotlin.reflect.KClass

class GameModelContext<T: Any, C> : GameModel<T, C> {
    var playerCount: IntRange = 2..2
    lateinit var factory: GameFactoryScope<C>.(C?) -> T
    lateinit var config: () -> C
    var onStart: GameStartScope<T>.() -> Unit = {}

    override fun players(playerCount: IntRange) {
        this.playerCount = playerCount
    }

    override fun playersFixed(playerCount: Int) {
        this.playerCount = playerCount..playerCount
    }

    override fun defaultConfig(creator: () -> C) {
        this.config = creator
    }

    override fun init(factory: GameFactoryScope<C>.(C?) -> T) {
        this.factory = factory
    }

    override fun onStart(effect: GameStartScope<T>.() -> Unit) {
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

class GameViewContext<T : Any>(
    private val gameObj: Game<T>,
    override val viewer: PlayerIndex
) : GameView<T> {
    override val game: T = gameObj.model
    private val requestable = mutableMapOf<String, GameViewOnRequestFunction<T>>()
    private val viewResult: MutableMap<String, Any?> = mutableMapOf()

    override fun result(): Map<String, Any?> {
        return viewResult.toMap()
    }

    override fun value(key: String, value: (T) -> Any?) {
        this.viewResult[key] = value(game)
    }

    override fun currentPlayer(function: (T) -> Int) {
        viewResult["currentPlayer"] = function(game)
    }

    override fun <P> grid(name: String, grid: GridDsl<T, P>, view: ViewDsl2D<T, P>) {
        val gridSpec = GameGridBuilder<T, P>(game)
        gridSpec.apply(grid as GameGridBuilder<T, P>.() -> Unit)
        val context = GameViewContext2D<T, P>(game)
        view(context)
        viewResult[name] = (0 until gridSpec.sizeY(game)).map {y ->
            (0 until gridSpec.sizeX(game)).map {x ->
                val p = gridSpec.get(game, x, y)
                context.view(p)
            }
        }
    }

    override fun eliminations() {
        val eliminationsDone = gameObj.eliminations.eliminations()
        viewResult["eliminations"] = (0 until gameObj.eliminations.playerCount).map {playerIndex ->
            val elimination = eliminationsDone.firstOrNull { it.playerIndex == playerIndex }
            if (elimination == null) {
                mapOf("eliminated" to false)
            } else {
                mapOf("eliminated" to true, "result" to elimination.winResult, "position" to elimination.position)
            }
        }
    }

    override fun onRequest(requestName: String, function: GameViewOnRequestFunction<T>) {
        this.requestable[requestName] = function
    }

    fun request(playerIndex: PlayerIndex, key: String, params: Map<String, Any>): Any? {
        val outerThis = this
        val gameViewOnRequestScope = object: GameViewOnRequestScope<T> {
            override val game: T
                get() = gameObj.model
            override val viewer: PlayerIndex
                get() = playerIndex

            override fun <A : Any> action(actionType: ActionType<T, A>): ActionView<T, A> = outerThis.action(actionType)
            override fun actions(): ActionsView<T> = outerThis.actions()
            override fun actionsChosen(): ActionsChosenView<T> = outerThis.actionsChosen()
        }
        return this.requestable[key]?.invoke(gameViewOnRequestScope, params)
    }

    override fun <A : Any> action(actionType: ActionType<T, A>): ActionView<T, A> {
        val chosen = gameObj.actions.choices.getChosen(viewer ?: -1)
        val chosenList = if (chosen?.actionType == actionType.name) chosen.chosen else emptyList()
        return ActionViewImpl(gameObj, actionType, PlayerViewer(playerIndex = viewer), chosenList)
    }

    override fun actions(): ActionsView<T> = ActionsViewImpl(gameObj, PlayerViewer(playerIndex = viewer), false)
    override fun actionsChosen(): ActionsChosenView<T> = ActionsViewImpl(gameObj, PlayerViewer(playerIndex = viewer), true)

}

class StateKeeper {
    // A single action needs to have multiple keys, so we can't use callback directly.
    private val currentAction = mutableMapOf<String, Any>()
    private val logEntries = mutableListOf<ActionLogEntry>()
    var replayMode = false

    fun lastMoveState(): Map<String, Any> = currentAction.toMap()
    fun clear() {
        currentAction.clear()
        replayMode = false
        logEntries.clear()
    }
    fun setState(state: Map<String, Any>) {
        replayMode = true
        currentAction.putAll(state)
        logEntries.clear()
    }

    fun save(key: String, value: Any) {
        currentAction[key] = value
    }

    operator fun get(key: String): Any? {
        return currentAction[key]
    }

    fun containsKey(key: String): Boolean {
        return currentAction.containsKey(key)
    }

    fun logs(): List<ActionLogEntry> = logEntries.toList()
    fun clearLogs() { logEntries.clear() }

    fun log(log: ActionLogEntry) {
        logEntries.add(log)
    }
}
class ReplayState(val stateKeeper: StateKeeper, override val eliminations: PlayerEliminationsWrite): EffectScope, ReplayableScope {
    private val mostRecent = mutableMapOf<String, Any>()

    override val replayable: ReplayableScope get() = this

    fun setReplayState(state: Map<String, Any>?) {
        stateKeeper.clear()
        if (state != null) {
            stateKeeper.setState(state)
        }
    }

    private fun <T: Any> replayable(key: String, default: () -> T): T {
        if (stateKeeper.containsKey(key) && !stateKeeper.replayMode) {
            throw IllegalStateException("State was already saved once for key $key. Use a different key")
        }
        if (stateKeeper.containsKey(key)) {
            return stateKeeper[key] as T
        }
        val value = default()
        mostRecent[key] = value
        stateKeeper.save(key, value)
        return value
    }

    override fun map(key: String, default: () -> Map<String, Any>): Map<String, Any> = replayable(key, default)
    override fun int(key: String, default: () -> Int): Int = replayable(key, default)
    override fun ints(key: String, default: () -> List<Int>): List<Int> = replayable(key, default)
    override fun string(key: String, default: () -> String): String = replayable(key, default)
    override fun strings(key: String, default: () -> List<String>): List<String> = replayable(key, default)
    override fun list(key: String, default: () -> List<Map<String, Any>>): List<Map<String, Any>> = replayable(key, default)
    override fun <E> randomFromList(key: String, list: List<E>, count: Int, stringMapper: (E) -> String): List<E> {
        val remainingList = list.toMutableList()
        val strings = strings(key) { remainingList.shuffled().take(count).map(stringMapper) }.toMutableList()
        val result = mutableListOf<E>()
        while (strings.isNotEmpty()) {
            val next = strings.removeLast()
            val item = remainingList.removeAt(remainingList.indexOfFirst { stringMapper(it) == next })
            result.add(item)
        }
        if (result.size != count) throw IllegalStateException("Size mismatch: Was ${result.size} but expected $count")
        return result
    }

}

class GameDslContext<T : Any> : GameDsl<T> {
    override fun <P> gridSpec(spec: GameGrid<T, P>.() -> Unit): GridDsl<T, P> {
        return spec
    }

    lateinit var configClass: KClass<*>
    lateinit var modelDsl: GameModelDsl<T, Any>
    var viewDsl: GameViewDsl<T>? = null
    var flowRulesDsl: GameFlowRulesDsl<T>? = null
    var flowDsl: GameFlowDsl<T>? = null
    var actionRulesDsl: GameActionRulesDsl<T>? = null
    val testCases: MutableList<GameTestCaseContext<T>> = mutableListOf()

    val model = GameModelContext<T, Any>()

    override fun <C : Any> setup(configClass: KClass<C>, modelDsl: GameModelDsl<T, C>) {
        this.configClass = configClass
        this.modelDsl = modelDsl as GameModelDsl<T, Any>
    }

    override fun setup(modelDsl: GameModelDsl<T, Unit>) {
        return this.setup(Unit::class, modelDsl)
    }

    override fun view(viewDsl: GameViewDsl<T>) {
        this.viewDsl = viewDsl
    }

    override fun actionRules(actionRulesDsl: GameActionRulesDsl<T>) {
        this.actionRulesDsl = actionRulesDsl
    }

    override fun testCase(players: Int, testDsl: GameTestDsl<T>) {
        this.testCases.add(GameTestCaseContext(players, testDsl))
    }

    override fun gameFlow(flowDsl: GameFlowDsl<T>) {
        this.flowDsl = flowDsl
    }

    fun createGame(playerCount: Int, config: Any, stateKeeper: StateKeeper): Game<T> {
        val flowDslNull = this.flowDsl == null
        val flowRulesNull = this.flowRulesDsl == null
        if (listOf(flowDslNull, flowRulesNull).distinct().size > 1) {
            throw IllegalStateException("when using one of gameFlow and gameFlowRules, the others must be used too")
        }

        return if (this.flowDsl == null) {
            GameImpl(this, playerCount, config, stateKeeper)
        } else {
            GameFlowImpl(this, playerCount, config, stateKeeper)
        }
    }

    override fun gameFlowRules(flowRulesDsl: GameFlowRulesDsl<T>) {
        this.flowRulesDsl = flowRulesDsl
    }

}
