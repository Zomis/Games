package net.zomis.games.dsl

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.zomis.games.dsl.impl.FlowStep
import net.zomis.games.dsl.impl.Game
import net.zomis.games.listeners.ReplayingListener
import kotlin.reflect.KClass

class ReplayException(override val message: String?, override val cause: Throwable?): Exception(message, cause)

class Replay<T : Any>(
    private val coroutineScope: CoroutineScope,
    gameSpec: GameSpec<T>,
    private val replayData: ReplayData,
    private val actionConverter: (KClass<*>, Any) -> Any = { _, it -> it }
): GameListener {
    val config = replayData.config
    val playerCount = replayData.playerCount

    suspend fun goToStart(): Replay<T> = this.gotoPosition(0)
    suspend fun goToEnd(): Replay<T> = this.gotoPosition(replayData.actions.size)

    private val entryPoint = GamesImpl.game(gameSpec)
    private var position: Int = 0
    private var targetPosition = 0
    lateinit var game: Game<T>
    lateinit var syncer: Syncer

    class Syncer: GameListener {
        val mutex = Mutex()
        var ready = false

        override suspend fun handle(coroutineScope: CoroutineScope, step: FlowStep) {
            mutex.withLock {
                this.ready = step is FlowStep.ProceedStep
                println("Set ready: $step")
            }
        }
        suspend fun sync(block: suspend () -> Unit) {
            mutex.withLock {
                println("Sync await ready")
                while (!ready) {
                    delay(10)
                }
                println("Sync await ready done")
            }
            println("Performing block")
            block.invoke()
        }
    }

    suspend fun gotoPosition(newPosition: Int): Replay<T> {
        this.targetPosition = newPosition
        if (newPosition < this.position) {
            restart()
        }
        return this
    }

    override suspend fun handle(coroutineScope: CoroutineScope, step: FlowStep) {
        when (step) {
            is FlowStep.AwaitInput -> {
                coroutineScope.launch {
                    if (position < targetPosition) stepForward()
                }
            }
            is FlowStep.ActionPerformed<*> -> {
                this.position++
            }
        }
    }

    private suspend fun stepForward() {
        println("Step forward $position")
        val action = replayData.actions[this.position]
        try {
            syncer.sync {
                println("Checking actionables on position $position. $action")
                println(game.view(0))
                val actionType = game.actions.type(action.actionType)!!
                val converted = actionConverter.invoke(actionType.actionType.serializedType, action.serializedParameter)
                println("Converted: $converted (${converted::class}) for $actionType")
                val actionable = actionType
                    .createActionFromSerialized(action.playerIndex, converted)
                game.actionsInput.send(actionable)
            }
        } catch (e: Exception) {
            throw ReplayException("Unable to perform action ${this.position}: $action", e)
        }
    }

    private suspend fun restart() {
        this.syncer = Syncer()
        this.game = entryPoint.setup().startGameWithConfig(coroutineScope, playerCount, config) {
            listOf(ReplayingListener(replayData), syncer, this)
        }
        this.position = 0
    }

    companion object {
        suspend fun <T: Any> initReplay(
            coroutineScope: CoroutineScope,
            gameSpec: GameSpec<T>,
            replayData: ReplayData,
            actionConverter: (KClass<*>, Any) -> Any = { _, it -> it }
        ): Replay<T> {
            require(replayData.gameType == gameSpec.name) {
                "Mismatching gametypes: Replay data for ${replayData.gameType} cannot be used on ${gameSpec.name}"
            }
            val replay = Replay(coroutineScope, gameSpec, replayData, actionConverter)
            replay.restart()
            return replay
        }
    }

}
