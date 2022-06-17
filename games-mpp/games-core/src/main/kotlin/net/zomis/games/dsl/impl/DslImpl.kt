package net.zomis.games.dsl.impl

import net.zomis.games.PlayerEliminationsWrite
import net.zomis.games.common.PlayerIndex
import net.zomis.games.dsl.*
import net.zomis.games.dsl.flow.GameFlowImpl
import kotlin.reflect.KClass

class GameModelContext<T: Any, C>(val configs: MutableList<GameConfig<Any>>) : GameModel<T, C> {
    var playerCount: IntRange = 2..2
    lateinit var factory: GameFactoryScope<C>.() -> T
    var onStart: GameStartScope<T>.() -> Unit = {}

    override fun players(playerCount: IntRange) {
        this.playerCount = playerCount
    }

    override fun playersFixed(playerCount: Int) {
        this.playerCount = playerCount..playerCount
    }

    override fun defaultConfig(creator: () -> C) {
        check(configs.none { it.key == "" })
        this.configs.add(GameConfigImpl("") { creator.invoke() as Any })
    }

    override fun init(factory: GameFactoryScope<C>.() -> T) {
        this.factory = factory
    }

    override fun onStart(effect: GameStartScope<T>.() -> Unit) {
        this.onStart = effect
    }
}

class GameViewContext<T : Any>(
    private val gameObj: Game<T>,
    override val viewer: PlayerIndex
) : GameView<T> {
    override val game: T = gameObj.model
    private val requestable = mutableMapOf<String, GameViewOnRequestFunction<T>>()
    private val viewResult: MutableMap<String, Any?> = mutableMapOf()

    fun result(): Map<String, Any?> {
        return viewResult.toMap()
    }

    override fun value(key: String, value: (T) -> Any?) {
        this.viewResult[key] = value(game)
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
            override fun <A : Any> actionRaw(actionType: ActionType<T, A>): ActionView<T, A> = outerThis.actionRaw(actionType)
            override fun <A : Any> chosenActions(actionType: ActionType<T, A>): ActionView<T, A> = outerThis.chosenActions(actionType)
        }
        return this.requestable[key]?.invoke(gameViewOnRequestScope, params)
    }

    override fun <A : Any> action(actionType: ActionType<T, A>): ActionView<T, A> = this.chosenActions(actionType)

    override fun actions(): ActionsView<T> = ActionsViewImpl(gameObj, PlayerViewer(playerIndex = viewer), false)
    override fun actionsChosen(): ActionsChosenView<T> = ActionsViewImpl(gameObj, PlayerViewer(playerIndex = viewer), true)
    override fun <A : Any> actionRaw(actionType: ActionType<T, A>): ActionView<T, A> {
        return ActionViewImpl(gameObj, actionType, PlayerViewer(playerIndex = viewer), emptyList())
    }

    override fun <A : Any> chosenActions(actionType: ActionType<T, A>): ActionView<T, A> {
        val chosen = gameObj.actions.choices.getChosen(viewer ?: -1)
        val chosenList = if (chosen?.actionType == actionType.name) chosen.chosen else emptyList()
        return ActionViewImpl(gameObj, actionType, PlayerViewer(playerIndex = viewer), chosenList)
    }

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
class ReplayState(
    val stateKeeper: StateKeeper,
    override val eliminations: PlayerEliminationsWrite,
    val config: GameConfigs
): EffectScope, ReplayableScope {
    private val mostRecent = mutableMapOf<String, Any>()

    override val replayable: ReplayableScope get() = this
    override fun <E : Any> config(gameConfig: GameConfig<E>): E = config.get(gameConfig)

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

class GameConfigImpl<E: Any>(override val key: String, override val default: () -> E): GameConfig<E> {
    var isMutable = false
    override var value: E = default()
    override val clazz: KClass<E> get() = value::class as KClass<E>
    override fun withDefaults(): GameConfig<E> = GameConfigImpl(key, default).also { it.isMutable = isMutable }

    override fun mutable(): GameConfig<E> {
        isMutable = true
        return this
    }

}
class GameDslContext<T : Any> : GameDsl<T> {
    lateinit var modelDsl: GameModelDsl<T, Any>
    var viewDsl: GameViewDsl<T>? = null
    var flowRulesDsl: GameFlowRulesDsl<T>? = null
    var flowDsl: GameFlowDsl<T>? = null
    var actionRulesDsl: GameActionRulesDsl<T>? = null
    private var configs = mutableListOf<GameConfig<Any>>()
    val testCases: MutableList<GameTestCaseContext<T>> = mutableListOf()

    val model = GameModelContext<T, Any>(configs)

    override fun <C : Any> setup(configClass: KClass<C>, modelDsl: GameModelDsl<T, C>) {
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

    fun createGame(playerCount: Int, config: GameConfigs, stateKeeper: StateKeeper): Game<T> {
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

    override fun <E: Any> config(key: String, default: () -> E): GameConfig<E> {
        val config = GameConfigImpl(key, default)
        this.configs.add(config as GameConfig<Any>)
        return config
    }

    fun configs(): GameConfigs = GameConfigs(configs.map { it.withDefaults() })

}