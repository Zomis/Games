package net.zomis.games.dsl.impl

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import net.zomis.games.PlayerEliminations
import net.zomis.games.PlayerEliminationsRead
import net.zomis.games.PlayerEliminationsWrite
import net.zomis.games.common.GameEvents
import net.zomis.games.common.PlayerIndex
import net.zomis.games.common.toSingleList
import net.zomis.games.dsl.*
import net.zomis.games.dsl.flow.GameForkResult
import net.zomis.games.dsl.listeners.BlockingGameListener
import net.zomis.games.listeners.ReplayListener
import net.zomis.games.scorers.Scorer
import net.zomis.games.scorers.ScorerController

const val DEBUG = false

inline fun debugPrint(message: String) {
    if (DEBUG) println(message)
}

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

class GameSetupImpl<T : Any>(gameSpec: GameSpec<T>) {

    val gameType: String = gameSpec.name
    internal val context = GameDslContext(gameSpec)
    init {
        gameSpec(context)
        context.modelDsl(context.model)
        if (context.useRandomAI) {
            context.ai("#AI_Random") {
                action {
                    GameAIs.randomActionable(game, playerIndex)
                }
            }
        }
    }

    val scorers: List<Scorer<T, out Any>> get() = context.createdScorers
    val scorerAIs: List<ScorerController<T>> get() = context.createdAIs
    val otherAIs: List<GameAI<T>> get() = context.otherAIs
    val playersCount: IntRange = context.model.playerCount
    fun ais(): List<GameAI<T>> = scorerAIs.map { it.gameAI() } + otherAIs
    fun findAI(name: String): GameAI<T>? {
        return scorerAIs.find { it.name == name }?.gameAI()
            ?: otherAIs.find { it.name == name }
    }

    fun configs(): GameConfigs = context.configs()

    suspend fun startGame(coroutineScope: CoroutineScope, playerCount: Int, flowListeners: (Game<Any>) -> Collection<GameListener>): Game<T>
        = startGameWithConfig(coroutineScope, playerCount, configs(), flowListeners)

    suspend fun startGameWithConfig(coroutineScope: CoroutineScope, playerCount: Int, config: GameConfigs, flowListeners: (Game<Any>) -> Collection<GameListener>): Game<T>
        = startGameWithInfo(coroutineScope, GameStartInfo(playerCount, config, fork = {false}), flowListeners)

    suspend fun startGameWithInfo( coroutineScope: CoroutineScope, startInfo: GameStartInfo, flowListeners: (Game<Any>) -> Collection<GameListener>): Game<T> {
        if (startInfo.playerCount !in playersCount) {
            throw IllegalArgumentException("Invalid number of players: ${startInfo.playerCount}, expected $playersCount")
        }
        val replayListener = ReplayListener(context.gameType)
        val game = context.createGame(startInfo) { mostRecentAction ->
            val replayData = when (mostRecentAction) {
                null -> replayListener.data()
                is FlowStep.ActionPerformed<*> -> replayListener.data().addAction(mostRecentAction.toActionReplay())
                is FlowStep.GameSetup<*> -> replayListener.data().copy(initialState = mostRecentAction.state)
                else -> throw IllegalArgumentException("Copier was called with unknown parameter: $mostRecentAction of type (${mostRecentAction::class})")
            }

            val blockingGameListener = BlockingGameListener()
            val replay = GamesImpl.game(context.gameSpec).replay(coroutineScope, replayData, gameListeners = {
                listOf(blockingGameListener)
            }, fork = true).goToEnd().awaitCatchUp()

            val gameCopy = replay.game
            GameForkResult(gameCopy, blockingGameListener, replayData = replayData)
        }

        val listeners = replayListener.toSingleList() + flowListeners.invoke(game as Game<Any>)
        debugPrint("Waiting for ${listeners.size} listeners")
        coroutineScope.launch(CoroutineName("Listeners for $game")) {
            debugPrint("[${this.coroutineContext[CoroutineName]!!.name}] Started")
            for (flowStep in game.feedbackFlow) {
                debugPrint("[${this.coroutineContext[CoroutineName]!!.name}] Listener feedback: $flowStep")
                listeners.forEach { listener ->
                    debugPrint("Listener $listener handling $flowStep")
                    listener.handle(coroutineScope, flowStep)
                    debugPrint("Listener $listener finished $flowStep")
                }
                if (flowStep is FlowStep.Completable) {
                    flowStep.complete()
                }
                if (flowStep is FlowStep.GameEnd) {
                    debugPrint("Game end, no more listeners")
                    break
                }
            }
        }
        debugPrint("Game ready to start")
        game.start(coroutineScope)
        debugPrint("Started, or something")
        return game
    }

}

@DslMarker
annotation class GameMarker

interface GameFork<T: Any> {
    val model: T
    val eliminations: PlayerEliminationsRead

    val gameType: String
    val playerCount: Int
    val playerIndices: IntRange get() = 0 until playerCount
    suspend fun copy(): GameForkResult<T>
    fun isGameOver(): Boolean
    fun isRunning() = !isGameOver()
    fun view(playerIndex: PlayerIndex): Map<String, Any?>
}

interface Game<T: Any>: GameFork<T> {
    val config: Any
    override val eliminations: PlayerEliminationsWrite
    val actions: Actions<T>
    val actionsInput: Channel<Actionable<T, out Any>>
    val feedbackFlow: Channel<FlowStep>
    suspend fun start(coroutineScope: CoroutineScope)
    fun stop()
}

class GameImpl<T : Any>(
    private val setupContext: GameDslContext<T>,
    override val playerCount: Int,
    val gameConfig: GameConfigs,
    private val copier: suspend () -> GameForkResult<T>
): Game<T>, GameFactoryScope<Any>, GameEventsExecutor {
    private val stateKeeper = StateKeeper()
    override val gameType: String = setupContext.gameType
    override fun toString(): String = "${super.toString()}-$gameType"

    private var actionsInputJob: Job? = null
    override val config: Any get() = gameConfig.oldStyleValue()
    override val eliminationCallback = PlayerEliminations(playerCount)
    override val eliminations: PlayerEliminationsWrite get() = eliminationCallback
    override val model = setupContext.model.factory(this)
    private val replayState = ReplayState(stateKeeper, eliminationCallback, gameConfig)
    private val rules = GameActionRulesContext(gameConfig, model, replayState, eliminationCallback)

    override suspend fun start(coroutineScope: CoroutineScope) {
        if (this.actionsInputJob != null) throw IllegalStateException("Game already started")
        feedbackFlow.send(FlowStep.GameStarted(this, gameConfig))
        replayState.stateKeeper.preSetup(this) { feedbackFlow.send(it) }
        setupContext.model.onStart(GameStartContext(gameConfig, model, replayState, playerCount))
        setupContext.actionRulesDsl?.invoke(rules)
        rules.gameStart()
        feedbackFlow.send(FlowStep.GameSetup(this, gameConfig, stateKeeper.lastMoveState()))
        val gameImpl = this

        this.actionsInputJob = coroutineScope.launch(CoroutineName("Actions job for $this")) {
            for (action in actionsInput) {
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
                    awaitInput()
                }
                if (isGameOver()) {
                    break
                }
            }
        }
        awaitInput()
    }

    override fun stop() {
        this.feedbackFlow.close()
        this.actionsInputJob?.cancel()
        this.actionsInputJob = null
    }

    private suspend fun awaitInput() {
        stateKeeper.logs().toList().forEach {
            feedbackFlow.send(FlowStep.Log(it))
        }
        stateKeeper.clearLogs()
        feedbackFlow.send(if (this.isGameOver()) FlowStep.GameEnd else FlowStep.AwaitInput)
    }

    override val actions = ActionsImpl(model, rules, replayState)

    override val actionsInput: Channel<Actionable<T, out Any>> = Channel()
    override val feedbackFlow: Channel<FlowStep> = Channel()

    override suspend fun copy(): GameForkResult<T> = copier.invoke()

    fun quickCopy(quickCopier: (source: T, destination: T) -> Unit): GameImpl<T> {
        val copy = GameImpl(setupContext, playerCount, gameConfig, copier)
        quickCopier.invoke(this.model, copy.model)
        copy.setupContext.actionRulesDsl?.invoke(copy.rules) // TODO: This is a bit of an ugly hack to add the rules
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
