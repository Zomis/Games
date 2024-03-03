package net.zomis.games.dsl

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.zomis.games.dsl.impl.FlowStep
import net.zomis.games.dsl.impl.Game
import net.zomis.games.dsl.impl.GameStartInfo
import net.zomis.games.dsl.listeners.BlockingGameListener
import net.zomis.games.listeners.ReplayingListener
import kotlin.reflect.KClass

class ReplayException(override val message: String?, override val cause: Throwable?): Exception(message, cause) {
    constructor(message: String?): this(message, null)
}

class Replay<T : Any>(
    private val coroutineScope: CoroutineScope,
    gameSpec: GameSpec<T>,
    private val replayData: ReplayData,
    private val actionConverter: (KClass<*>, Any) -> Any = { _, it -> it },
    private val gameListeners: (Game<T>) -> List<GameListener>,
    private val fork: () -> Boolean
): GameListener {
    val config = replayData.config
    val playerCount = replayData.playerCount

    suspend fun goToStart(): Replay<T> = this.gotoPosition(0)
    suspend fun goToEnd(): Replay<T> = this.gotoPosition(replayData.actions.size)

    private val entryPoint = GamesImpl.game(gameSpec)
    private var actionsSent: Int = 0
    private var actionsPerformed: Int = 0
    private var targetPosition = 0
    private var internalGame: Game<T>? = null
    val game get() = internalGame!!
    private var blockingListener: BlockingGameListener = BlockingGameListener()
    private val replayCompleteLock = Mutex(locked = true)

    suspend fun awaitCatchUp(): Replay<T> {
        replayCompleteLock.withLock {  }
        return this
    }

    private fun remainingActions(fromIndex: Int, toIndex: Int): Flow<ActionReplay> {
        return replayData.actions.subList(fromIndex, toIndex).asFlow()
    }

    suspend fun gotoPosition(newPosition: Int): Replay<T> {
        if (newPosition != this.actionsPerformed) {
            if (!this.replayCompleteLock.isLocked) this.replayCompleteLock.lock()
        }
        this.targetPosition = newPosition
        coroutineScope.launch(CoroutineName("Replay $this gotoPosition $newPosition")) {
            val previousActionsSent = actionsSent
            val actionsFlow = if (newPosition < previousActionsSent) {
                restart()
                remainingActions(0, newPosition)
            } else {
                remainingActions(actionsSent, newPosition)
            }
            actionsFlow.collect { action ->
                blockingListener.await()
                val actionType = game.actions.type(action.actionType)
                    ?: throw IllegalStateException("Unable to perform action $actionsSent. '${action.actionType}' does not exist: $action")
                val converted = actionConverter.invoke(actionType.actionType.serializedType, action.serializedParameter)
                val actionable = actionType.createActionFromSerialized(action.playerIndex, converted)
                actionsSent++
                blockingListener.awaitAndPerform(actionable)
                blockingListener.await()
            }
            if (replayCompleteLock.isLocked) {
                replayCompleteLock.unlock()
            }
        }
        return this
    }

    override suspend fun handle(coroutineScope: CoroutineScope, step: FlowStep) {
        if (step is FlowStep.ActionPerformed<*>) actionsPerformed++
        if (step is FlowStep.IllegalAction) {
            throw ReplayException("Action is not allowed: $step after $actionsPerformed successfully performed actions.", null)
        }
    }

    private suspend fun restart() {
        this.blockingListener = BlockingGameListener()
        this.internalGame?.stop()
        this.actionsSent = 0
        this.actionsPerformed = 0
        this.internalGame = entryPoint.setup().startGameWithInfo(coroutineScope, GameStartInfo(playerCount, config, fork)) {
            listOf(ReplayingListener(replayData), blockingListener, this) + gameListeners.invoke(it as Game<T>)
        }
    }

    companion object {
        suspend fun <T: Any> initReplay(
            coroutineScope: CoroutineScope,
            gameSpec: GameSpec<T>,
            replayData: ReplayData,
            actionConverter: (KClass<*>, Any) -> Any = { _, it -> it },
            gameListeners: (Game<T>) -> List<GameListener>,
            fork: () -> Boolean,
        ): Replay<T> {
            require(replayData.gameType == gameSpec.name) {
                "Mismatching gametypes: Replay data for ${replayData.gameType} cannot be used on ${gameSpec.name}"
            }
            val replay = Replay(coroutineScope, gameSpec, replayData, actionConverter, gameListeners, fork)
            replay.restart()
            return replay
        }
    }

}
