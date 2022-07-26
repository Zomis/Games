package net.zomis.games.dsl

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.zomis.games.dsl.impl.FlowStep
import net.zomis.games.dsl.impl.Game
import net.zomis.games.dsl.impl.GameStartInfo
import net.zomis.games.dsl.listeners.BlockingGameListener
import net.zomis.games.listeners.ReplayingListener
import kotlin.reflect.KClass

class ReplayException(override val message: String?, override val cause: Throwable?): Exception(message, cause)

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
    private val stepLock = Mutex()
    private val replayCompleteLock = Mutex(locked = true)
    private var stepJob: Job? = null

    suspend fun awaitCatchUp(): Replay<T> {
//        while (targetPosition > position) {
//            yield() // Infinite loop if we don't add this here
//            blockingListener.await()
//        }
        replayCompleteLock.withLock {  }
        return this
    }
    suspend fun gotoPosition(newPosition: Int): Replay<T> {
        println("Replay targeting $newPosition, current is $targetPosition sent $actionsSent performed $actionsPerformed")
        this.targetPosition = newPosition
        if (newPosition < this.actionsPerformed) {
            restart()
        }
        if (targetPosition != this.actionsPerformed) {
            if (!this.replayCompleteLock.isLocked) this.replayCompleteLock.lock()
        }
        possiblyStepForward()
        return this
    }

    override suspend fun handle(coroutineScope: CoroutineScope, step: FlowStep) {
        when (step) {
            is FlowStep.AwaitInput -> {
                possiblyStepForward()
                blockingListener.await()
                println("sent $actionsSent performed $actionsPerformed is maybe caught up with $targetPosition, fippling with lock $replayCompleteLock $this with game ${this.internalGame}")
                if (this.actionsSent == actionsPerformed && this.actionsPerformed == targetPosition) {
                    if (this.replayCompleteLock.isLocked) this.replayCompleteLock.unlock()
                } else {
                    if (!this.replayCompleteLock.isLocked) this.replayCompleteLock.lock()
                }
            }
            is FlowStep.ActionPerformed<*> -> {
                actionsPerformed++
            }
        }
    }

    private suspend fun possiblyStepForward() {
        println("Awaiting stepLock to step forward $stepLock")
        stepLock.withLock {

            val job = stepJob
            println("Steplock acquired $stepLock. Job is $job")
            if (job != null && job.isActive) return@withLock
            if (actionsSent < targetPosition) {
                println("Stepping forward in $this")
                stepJob = coroutineScope.launch(CoroutineName("stepJob for replay $this ($internalGame) sent $actionsSent target $targetPosition performed $actionsPerformed")) {
                    println("Calling stepForward from replay launched coroutine")
                    stepForward()
                }
            }
        }
    }

    private suspend fun stepForward() {
        println("Step forward sent $actionsSent performed $actionsPerformed target $targetPosition")
        val actionIndex = this.actionsSent
        val action = replayData.actions[actionIndex]
        try {
            blockingListener.await()
            println("Checking actionables on position $actionsSent. $action")
            println(game.view(0))
            val actionType = game.actions.type(action.actionType)!!
            val converted = actionConverter.invoke(actionType.actionType.serializedType, action.serializedParameter)
            println("Converted: $converted (${converted::class}) for $actionType")
            val actionable = actionType
                .createActionFromSerialized(action.playerIndex, converted)
            println("Sending action from $this to $game: $action")
            actionsSent++
            ///blockingListener.awaitAndPerform(actionable) // this line instead of the one below only makes things worse, and makes it hang (probably deadlock?)
            game.actionsInput.send(actionable)
        } catch (e: Exception) {
            throw ReplayException("Unable to perform action $actionIndex: $action", e)
        }
    }

    private suspend fun restart() {
        this.blockingListener = BlockingGameListener()
        this.internalGame?.stop()
        println("Restarting replay $this with scope $coroutineScope")
        this.actionsSent = 0
        this.actionsPerformed = 0
        this.internalGame = entryPoint.setup().startGameWithInfo(coroutineScope, GameStartInfo(playerCount, config, fork)) {
            listOf(ReplayingListener(replayData), blockingListener, this) + gameListeners.invoke(it as Game<T>)
        }
        println("Restarted replay $this")
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
            println("Creating replay using $replayData")
            require(replayData.gameType == gameSpec.name) {
                "Mismatching gametypes: Replay data for ${replayData.gameType} cannot be used on ${gameSpec.name}"
            }
            val replay = Replay(coroutineScope, gameSpec, replayData, actionConverter, gameListeners, fork)
            replay.restart()
            return replay
        }
    }

}
