package net.zomis.games.dsl.impl

import kotlinx.coroutines.*
import net.zomis.games.ais.noAvailableActions
import net.zomis.games.dsl.Actionable
import net.zomis.games.dsl.GameListener
import net.zomis.games.dsl.GameListenerFactory
import net.zomis.games.scorers.Scorer
import net.zomis.games.scorers.ScorerController

interface GameAIScope<T: Any> {
    val game: Game<T>
    val playerIndex: Int
    fun <L: GameListener> listener(gameListener: () -> L): L
    fun requiredAI(ai: () -> GameAI<T>): GameAIDependency<T>
    fun action(block: suspend GameAIActionScope<T>.() -> Actionable<T, out Any>?)
    fun queryable(block: suspend (GameControllerScope<T>) -> Any)
}

object GameAIs {
    fun <T: Any> randomActionable(game: Game<T>, playerIndex: Int): Actionable<T, Any>? {
        val actionTypes = game.actions.types().filter { it.availableActions(playerIndex, null).any() }
        if (actionTypes.isEmpty()) {
            return null
        }
        val actionType = actionTypes.random()
        if (!actionType.isComplex()) {
            return actionType.availableActions(playerIndex, null).shuffled().random()
        }

        val chosen = mutableListOf<Any>()
        while (true) {
            val next = actionType.withChosen(playerIndex, chosen)
            val options = next.nextOptions().toList()
            val parameters = next.parameters().toList()
            if (options.isEmpty() && parameters.isEmpty()) {
                check(chosen.isNotEmpty())
                // This path didn't go anywhere, choose another path.
                chosen.clear()
                continue
            }
            val random = (0 until (options.size + parameters.size)).random()
            if (random >= options.size) {
                val actionable = actionType.createAction(playerIndex, parameters[random - options.size].parameter)
                if (actionType.isAllowed(actionable)) {
                    return actionable
                } else {
                    println("randomActionable reset for player $playerIndex ${actionType.name}: $chosen")
                    chosen.clear()
                }
            } else {
                chosen.add(options[random].choiceValue)
            }
        }
    }
}

class GameAIDependency<T: Any>(val gameAIListener: GameAIListener<T>)

interface GameAIActionScope<T: Any> : GameControllerScope<T> {
    var delay: Int
    suspend fun byScorers(vararg scorers: Scorer<T, out Any>): Actionable<T, out Any>?
    suspend fun byAI(ai: GameAIDependency<T>): Actionable<T, out Any>?
    fun randomAction(): Actionable<T, Any>?
}

class GameAIContext<T: Any>(override val game: Game<T>, override val playerIndex: Int): GameAIScope<T> {
    var delayOverride: Long? = null

    private val listeners = mutableListOf<GameListener>()
    internal var queryBlock: suspend (GameControllerScope<T>) -> Any = {  }
    internal var actionBlock: suspend GameAIActionScope<T>.() -> Actionable<T, out Any>? = {
        throw UnsupportedOperationException("action block missing")
    }

    suspend fun runListeners(coroutineScope: CoroutineScope, step: FlowStep) {
        listeners.forEach { it.handle(coroutineScope, step) }
    }

    override fun <L : GameListener> listener(gameListener: () -> L): L = gameListener.invoke().also { listeners.add(it) }

    override fun requiredAI(ai: () -> GameAI<T>): GameAIDependency<T> {
        val gameAI = ai.invoke()
        val listener = gameAI.gameListener(game, playerIndex).also { it.enabled = false }
        listeners.add(listener)
        return GameAIDependency(listener)
    }

    override fun action(block: suspend GameAIActionScope<T>.() -> Actionable<T, out Any>?) {
        this.actionBlock = block
    }

    override fun queryable(block: suspend (GameControllerScope<T>) -> Any) {
        this.queryBlock = block
    }

    suspend fun gimmeAction(): Actionable<T, out Any>? {
        val actionContext = GameAIActionContext(game, playerIndex)
        return actionBlock.invoke(actionContext)
    }

    val aiListener = GameAIListener(this) { delayOverride }
}

class GameAIActionContext<T: Any>(
    override val game: Game<T>,
    override val playerIndex: Int
) : GameAIActionScope<T> {
    override var delay: Int = 500

    override suspend fun byScorers(vararg scorers: Scorer<T, out Any>): Actionable<T, out Any>? {
        return ScorerController(game.gameType, "_", *(scorers as Array<out Scorer<T, Any>>))
            .gameAI().simpleAction(game, playerIndex)
    }

    override suspend fun byAI(ai: GameAIDependency<T>): Actionable<T, out Any>? {
        return ai.gameAIListener.context.gimmeAction()
    }

    override fun randomAction(): Actionable<T, Any>? = GameAIs.randomActionable(game, playerIndex)

    override val model: T get() = game.model

}

class GameAIListener<T: Any>(val context: GameAIContext<T>, private val delayOverride: () -> Long?): GameListener {
    var enabled = true
    var job: Job? = null

    override suspend fun handle(coroutineScope: CoroutineScope, step: FlowStep) {
        context.runListeners(coroutineScope, step)
        if (step is FlowStep.GameEnd) {
            job?.cancel()
        }
        if (step is FlowStep.AwaitInput) {
            if (!enabled) return
            if (noAvailableActions(context.game, context.playerIndex)) return

            val actionContext = GameAIActionContext(context.game, context.playerIndex)
            val action = context.actionBlock.invoke(actionContext) ?: return
            val sleepDelay = delayOverride.invoke() ?: actionContext.delay.toLong()

            job?.cancel()
            job = coroutineScope.launch(CoroutineName("GameAIListener $this gonna do $action in ${context.game} after $sleepDelay ms")) {
                delay(sleepDelay)
                if (context.game.actions.type(action.actionType)?.isAllowed(action as Actionable<T, Any>) == true) {
                    context.game.actionsInput.send(action)
                } else {
                    println("Skipping action from $this: $action as it is no longer allowed")
                }
            }
        }
    }
}

class GameAI<T: Any>(
    val name: String,
    val block: GameAIScope<T>.() -> Unit
) {
    fun invokedContext(game: Game<T>, playerIndex: Int): GameAIContext<T> = GameAIContext(game, playerIndex).also(block)
    fun gameListener(game: Game<T>, playerIndex: Int, delayOverride: Long? = null): GameAIListener<T> {
        val context = invokedContext(game, playerIndex)
        context.delayOverride = delayOverride
        return context.aiListener
    }

    suspend fun query(game: Game<T>, playerIndex: Int): Any {
        val context = invokedContext(game, playerIndex)
        return context.queryBlock.invoke(GameControllerContext(game, playerIndex))
    }

    fun listenerFactory(): GameListenerFactory = GameListenerFactory { game, playerIndex -> gameListener(game as Game<T>, playerIndex) }

    suspend fun simpleAction(game: Game<T>, playerIndex: Int): Actionable<T, out Any>? {
        val context = invokedContext(game, playerIndex)
        return context.aiListener.context.gimmeAction()
    }

    override fun toString(): String = "GameAI:$name"

}
