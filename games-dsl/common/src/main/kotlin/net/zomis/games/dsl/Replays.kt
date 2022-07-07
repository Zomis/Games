package net.zomis.games.dsl

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
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
    lateinit var game: Game<T>
    lateinit var blockingListener: BlockingGameListener

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
        this.game = entryPoint.setup().startGameWithConfig(coroutineScope, playerCount, config) {
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
