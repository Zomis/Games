package net.zomis.games.dsl.impl

import net.zomis.games.PlayerEliminations
import net.zomis.games.common.GameEvents
import net.zomis.games.common.PlayerIndex
import net.zomis.games.dsl.*
import kotlin.reflect.KClass

class GameControllerContext<T : Any>(
    override val game: Game<T>, override val playerIndex: Int
): GameControllerScope<T> {
    override val model: T get() = game.model
    fun view(): Map<String, Any?> = game.view(playerIndex)
}
interface GameControllerScope<T : Any> {
    val game: Game<T>
    val model: T
    val playerIndex: Int
}
typealias GameController<T> = (GameControllerScope<T>) -> Actionable<T, Any>?

class GameSetupImpl<T : Any>(gameSpec: GameSpec<T>) {

    val gameType: String = gameSpec.name
    internal val context = GameDslContext<T>()
    init {
        gameSpec(context)
        context.modelDsl(context.model)
    }

    val playersCount: IntRange = context.model.playerCount

    fun configClass(): KClass<*> = context.configClass
    fun getDefaultConfig(): Any = if (configClass() == Unit::class) Unit else context.model.config()

    fun createGame(playerCount: Int, config: Any): Game<T>
        = this.createGameWithState(playerCount, config, StateKeeper())

    fun createGameWithState(playerCount: Int, config: Any, stateKeeper: StateKeeper): Game<T> {
        if (playerCount !in playersCount) {
            throw IllegalArgumentException("Invalid number of players: $playerCount, expected $playersCount")
        }
        return context.createGame(playerCount, config, stateKeeper)
    }

}

@DslMarker
annotation class GameMarker

interface Game<T: Any> {
    val playerCount: Int
    val playerIndices: IntRange get() = 0 until playerCount
    val config: Any
    val eliminations: PlayerEliminations
    val model: T
    val stateKeeper: StateKeeper
    val actions: Actions<T>
    fun copy(copier: (source: T, destination: T) -> Unit): Game<T>
    fun isGameOver(): Boolean
    fun view(playerIndex: PlayerIndex): Map<String, Any?>
    fun viewRequest(playerIndex: PlayerIndex, key: String, params: Map<String, Any>): Any?
}

class GameImpl<T : Any>(
    private val setupContext: GameDslContext<T>,
    override val playerCount: Int,
    override val config: Any,
    override val stateKeeper: StateKeeper
): Game<T>, GameFactoryScope<Any>, GameEventsExecutor {

    override val eliminationCallback = PlayerEliminations(playerCount)
    override val eliminations: PlayerEliminations get() = eliminationCallback
    override val model = setupContext.model.factory(this, config)
    private val replayState = ReplayState(stateKeeper, eliminationCallback)
    private val rules = GameActionRulesContext(model, replayState, eliminationCallback)
    init {
        setupContext.model.onStart(GameStartContext(model, replayState))
        setupContext.actionRulesDsl?.invoke(rules)
        setupContext.rulesDsl?.invoke(rules)
        rules.gameStart()
    }
    override val actions = ActionsImpl(model, rules, replayState)

    override fun copy(copier: (source: T, destination: T) -> Unit): GameImpl<T> {
        val copy = GameImpl(setupContext, playerCount, config, stateKeeper)
        copier(this.model, copy.model)
        this.eliminations.eliminations().forEach { copy.eliminations.eliminate(it) }
        return copy
    }

    override fun view(playerIndex: PlayerIndex): Map<String, Any?> {
        val view = GameViewContext(this, playerIndex)
        if (model is Viewable) {
            val map = model.toView(playerIndex) as Map<String, Any?>
            map.forEach { entry -> view.value(entry.key) { entry.value } }
        }
        setupContext.viewDsl?.invoke(view)
        rules.view(view)
        return view.result()
    }

    override fun isGameOver(): Boolean = eliminationCallback.isGameOver()

    override fun viewRequest(playerIndex: PlayerIndex, key: String, params: Map<String, Any>): Any? {
        val view = GameViewContext(this, playerIndex)
        setupContext.viewDsl?.invoke(view)
        return view.request(playerIndex, key, params)
    }

    override val events: GameEventsExecutor get() = this
    override fun <E> fire(executor: GameEvents<E>, event: E) = this.rules.fire(executor, event)
}
