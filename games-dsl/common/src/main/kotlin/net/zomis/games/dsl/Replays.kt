package net.zomis.games.dsl

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.zomis.games.dsl.impl.FlowStep
import net.zomis.games.dsl.impl.Game
import net.zomis.games.dsl.listeners.BlockingGameListener
import net.zomis.games.listeners.ReplayingListener
import kotlin.reflect.KClass

class ReplayException(override val message: String?, override val cause: Throwable?): Exception(message, cause)

class Replay<T : Any>(
    private val coroutineScope: CoroutineScope,
    gameSpec: GameSpec<T>,
    private val replayData: ReplayData,
    private val actionConverter: (KClass<*>, Any) -> Any = { _, it -> it },
    private val gameListeners: (Game<T>) -> List<GameListener>
): GameListener {
    val config = replayData.config
    val playerCount = replayData.playerCount

    suspend fun goToStart(): Replay<T> = this.gotoPosition(0)
    suspend fun goToEnd(): Replay<T> = this.gotoPosition(replayData.actions.size)

    private val entryPoint = GamesImpl.game(gameSpec)
    private var position: Int = 0
    private var targetPosition = 0
    private var internalGame: Game<T>? = null
    val game get() = internalGame!!
    private var blockingListener: BlockingGameListener = BlockingGameListener()
    private val stepLock = Mutex()
    private var stepJob: Job? = null

    suspend fun awaitCatchUp(): Replay<T> {
        while (targetPosition > position) {
            yield() // Infinite loop if we don't add this here
            blockingListener.await()
        }
        return this
    }
    suspend fun gotoPosition(newPosition: Int): Replay<T> {
        this.targetPosition = newPosition
        if (newPosition < this.position) {
            restart()
        }
        possiblyStepForward()
        return this
    }

    override suspend fun handle(coroutineScope: CoroutineScope, step: FlowStep) {
        when (step) {
            is FlowStep.AwaitInput -> {
                possiblyStepForward()
            }
            is FlowStep.ActionPerformed<*> -> {
                this.position++
            }
        }
    }

    private suspend fun possiblyStepForward() {
        stepLock.withLock {
            val job = stepJob
            if (job != null && job.isActive) return@withLock
            if (position < targetPosition) {
                stepJob = coroutineScope.launch {
                    println("Calling stepForward from replay launched coroutine")
                    stepForward()
                }
            }
        }
    }

    private suspend fun stepForward() {
        println("Step forward $position")
        val action = replayData.actions[this.position]
        try {
            blockingListener.await()
            println("Checking actionables on position $position. $action")
            println(game.view(0))
            val actionType = game.actions.type(action.actionType)!!
            val converted = actionConverter.invoke(actionType.actionType.serializedType, action.serializedParameter)
            println("Converted: $converted (${converted::class}) for $actionType")
            val actionable = actionType
                .createActionFromSerialized(action.playerIndex, converted)
            game.actionsInput.send(actionable)
        } catch (e: Exception) {
            throw ReplayException("Unable to perform action ${this.position}: $action", e)
        }
    }

    private suspend fun restart() {
        this.blockingListener = BlockingGameListener()
        this.internalGame?.stop()
        this.internalGame = entryPoint.setup().startGameWithConfig(coroutineScope, playerCount, config) {
            listOf(ReplayingListener(replayData), blockingListener, this) + gameListeners.invoke(it as Game<T>)
        }
        this.position = 0
    }

    companion object {
        suspend fun <T: Any> initReplay(
            coroutineScope: CoroutineScope,
            gameSpec: GameSpec<T>,
            replayData: ReplayData,
            actionConverter: (KClass<*>, Any) -> Any = { _, it -> it },
            gameListeners: (Game<T>) -> List<GameListener>
        ): Replay<T> {
            require(replayData.gameType == gameSpec.name) {
                "Mismatching gametypes: Replay data for ${replayData.gameType} cannot be used on ${gameSpec.name}"
            }
            val replay = Replay(coroutineScope, gameSpec, replayData, actionConverter, gameListeners)
            replay.restart()
            return replay
        }
    }

}
