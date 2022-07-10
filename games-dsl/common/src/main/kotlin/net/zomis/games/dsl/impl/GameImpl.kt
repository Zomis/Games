package net.zomis.games.dsl.impl

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import net.zomis.games.PlayerEliminations
import net.zomis.games.PlayerEliminationsWrite
import net.zomis.games.common.GameEvents
import net.zomis.games.common.PlayerIndex
import net.zomis.games.dsl.*

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
    internal val context = GameDslContext<T>(gameSpec.name)
    init {
        gameSpec(context)
        context.modelDsl(context.model)
    }

    val playersCount: IntRange = context.model.playerCount

    fun configs(): GameConfigs = context.configs()

    suspend fun startGame(coroutineScope: CoroutineScope, playerCount: Int, flowListeners: (Game<Any>) -> Collection<GameListener>): Game<T>
        = startGameWithConfig(coroutineScope, playerCount, configs(), flowListeners)

    suspend fun startGameWithConfig(coroutineScope: CoroutineScope, playerCount: Int, config: GameConfigs, flowListeners: (Game<Any>) -> Collection<GameListener>): Game<T> {
        if (playerCount !in playersCount) {
            throw IllegalArgumentException("Invalid number of players: $playerCount, expected $playersCount")
        }
        val game = context.createGame(playerCount, config, StateKeeper())

        val listeners = flowListeners.invoke(game as Game<Any>)
        println("Waiting for ${listeners.size} listeners")
        coroutineScope.launch(context = coroutineScope.coroutineContext + CoroutineName("$gameType with $playerCount players")) {
            for (flowStep in game.feedbackFlow) {
                println("Listener feedback: $flowStep")
                listeners.forEach { listener ->
                    println("Listener $listener handling $flowStep")
                    listener.handle(coroutineScope, flowStep)
                    println("Listener $listener finished $flowStep")
                }
                if (flowStep is FlowStep.Completable) {
                    flowStep.complete()
                }
                if (flowStep is FlowStep.GameEnd) {
                    println("Game end, no more listeners")
                    break
                }
            }
        }
        println("Game ready to start")
        game.start(coroutineScope)
        println("Started, or something")
        return game
    }

}

@DslMarker
annotation class GameMarker

interface Game<T: Any> {
    val gameType: String
    val playerCount: Int
    val playerIndices: IntRange get() = 0 until playerCount
    val config: Any
    val eliminations: PlayerEliminationsWrite
    val model: T
    val stateKeeper: StateKeeper // TODO: Hide `stateKeeper` and use FlowSteps as much as possible
    val actions: Actions<T>
    val actionsInput: Channel<Actionable<T, out Any>>
    val feedbackFlow: Channel<FlowStep>
    suspend fun start(coroutineScope: CoroutineScope)
    fun copy(copier: (source: T, destination: T) -> Unit): Game<T>
    fun isGameOver(): Boolean
    fun isRunning() = !isGameOver()
    fun view(playerIndex: PlayerIndex): Map<String, Any?>
    fun stop()
}

class GameImpl<T : Any>(
    private val setupContext: GameDslContext<T>,
    override val playerCount: Int,
    val gameConfig: GameConfigs,
    override val stateKeeper: StateKeeper
): Game<T>, GameFactoryScope<Any>, GameEventsExecutor {
    override val gameType: String = setupContext.gameType

    private var actionsInputJob: Job? = null
    override val config: Any get() = gameConfig.oldStyleValue()
    override val eliminationCallback = PlayerEliminations(playerCount)
    override val eliminations: PlayerEliminationsWrite get() = eliminationCallback
    override val model = setupContext.model.factory(this)
    private val replayState = ReplayState(stateKeeper, eliminationCallback, gameConfig)
    private val rules = GameActionRulesContext(gameConfig, model, replayState, eliminationCallback)

    override suspend fun start(coroutineScope: CoroutineScope) {
        if (this.actionsInputJob != null) throw IllegalStateException("Game already started")
        println("$this: pre-setup")
        replayState.stateKeeper.preSetup(this) { feedbackFlow.send(it) }
        setupContext.model.onStart(GameStartContext(gameConfig, model, replayState, playerCount))
        setupContext.actionRulesDsl?.invoke(rules)
        rules.gameStart()
        feedbackFlow.send(FlowStep.GameSetup(this, gameConfig, stateKeeper.lastMoveState()))
        println("$this: setup done")
        val gameImpl = this

        this.actionsInputJob = coroutineScope.launch {
            println("$gameImpl: actions job")
            for (action in actionsInput) {
                println("$gameImpl: GameImpl received action $action")
                stateKeeper.clear()
                val oldEliminations = eliminations.eliminations()
                replayState.stateKeeper.preMove(action) {
                    feedbackFlow.send(it)
                }
                val result = actions.type(action.actionType)?.perform(action.playerIndex, action.parameter)
                if (result != null) {
                    feedbackFlow.send(result as FlowStep)
                    eliminations.eliminations().minus(oldEliminations.toSet()).forEach {
                        feedbackFlow.send(FlowStep.Elimination(it))
                    }
                    println("$gameImpl: flow step sent: $result, now sending await input")
                    awaitInput()
                }
                if (isGameOver()) {
                    println("$gameImpl: actions job game over")
                    break
                }
            }
            println("$gameImpl: end of actions job")
        }
        println("$gameImpl: await input")
        awaitInput()
        println("$gameImpl: start done")
    }

    override fun stop() {
        this.feedbackFlow.close()
        this.actionsInputJob?.cancel()
        this.actionsInputJob = null
    }

    private suspend fun awaitInput() {
        feedbackFlow.send(if (this.isGameOver()) FlowStep.GameEnd else FlowStep.AwaitInput)
    }

    override val actions = ActionsImpl(model, rules, replayState)

    override val actionsInput: Channel<Actionable<T, out Any>> = Channel()
    override val feedbackFlow: Channel<FlowStep> = Channel()

    override fun copy(copier: (source: T, destination: T) -> Unit): GameImpl<T> {
        val copy = GameImpl(setupContext, playerCount, gameConfig, stateKeeper)
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
        rules.view(view)
        return view.result()
    }

    override fun isGameOver(): Boolean = eliminationCallback.isGameOver()

    override val events: GameEventsExecutor get() = this
    override fun <E> fire(executor: GameEvents<E>, event: E) = this.rules.fire(executor, event)
    override fun <E: Any> config(config: GameConfig<E>): E = gameConfig.get(config)
}
