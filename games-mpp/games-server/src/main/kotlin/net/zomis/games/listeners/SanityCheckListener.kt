package net.zomis.games.listeners

import klog.KLoggers
import kotlinx.coroutines.CoroutineScope
import net.zomis.games.dsl.*
import net.zomis.games.dsl.impl.FlowStep
import net.zomis.games.dsl.impl.Game
import net.zomis.games.dsl.listeners.BlockingGameListener
import net.zomis.games.server2.ServerGames
import kotlin.reflect.KClass

/*
* - Game actions should be replayable
*   - Prevent situations where a deserialized object is not the same object as the one in game, causing updates on something else
*   - Replaying actions should generate the same view as when the original action was made.
* - No Kotlin-specific data sent to frontend
*/
class SanityCheckListener(val game: Game<out Any>): GameListener {
    private val logger = KLoggers.logger(this)
    private val replayListener = ReplayListener(game.gameType)
    var replay: Replay<Any>? = null
    private val liveReplayingListener = LiveReplayingListener()
    private val blockingListener = BlockingGameListener()
    private val enumsFound = mutableSetOf<KClass<*>>()

    override suspend fun handle(coroutineScope: CoroutineScope, step: FlowStep) {
        println("Real game handling $step")
        replayListener.handle(coroutineScope, step)
        val replay = this.replay
        if (step is FlowStep.GameSetup<*>) {
            liveReplayingListener.setupState = step as FlowStep.GameSetup<Any>
            this.replay = ServerGames.entrypoint(game.gameType)!!
                .replay(coroutineScope, replayListener.data(), gameListeners = {
                    listOf(blockingListener, liveReplayingListener)
                })
            blockingListener.await()
        }

        if (step is FlowStep.ActionPerformed<*>) {
            // Perform the same action with the same state in the live replay game
            val replayGame = replay?.game ?: throw IllegalStateException("Replay game is not initialized")
            val actionType = replayGame.actions.type(step.action.actionType)
                ?: throw IllegalStateException("LiveReplay did not find actionType for: ${step.action}")
            liveReplayingListener.lastAction = step as FlowStep.ActionPerformed<Any>
            val replayAction = actionType.createActionFromSerialized(step.playerIndex, step.serializedParameter)

            println("Sending actionsInput $replayAction")
            blockingListener.awaitAndPerform(replayAction)
            println("Awaited result")
        }

        if (step is FlowStep.AwaitInput) {
            blockingListener.await()

            val replayGame = replay?.game ?: throw IllegalStateException("Replay game is not initialized")
            val game = this.game

            // make sure that view in this game is the same as in live replay game, for all players
            // also check view for all players for Kotlin-specific data
            for (playerIndex in game.playerIndices.toList()+null) {
                val originalView = game.view(playerIndex)
                val replayView = replayGame.view(playerIndex)
                checkViewTypes(originalView)
                check(originalView == replayView) {
                    "Original view:\n$originalView\ndiffers from replayView:\n$replayView"
                }
            }
        }
    }

    private fun checkViewTypes(data: Any?) {
        when (data) {
            null -> return
            is Int, is String, is Double, is Boolean -> return
            is List<*> -> data.forEach { checkViewTypes(it) }
            is Map<*, *> -> data.forEach {
                check(it.key is String) { "Keys in view data must be strings" }
                checkViewTypes(it.value)
            }
            is Enum<*> -> {
                if (enumsFound.add(data::class)) {
                    logger.warn { "Enum detected: $data of type ${data::class}. Enums in view are considered okay for now, but things may change" }
                }
            }
            else -> throw IllegalStateException("Unsupported view data: $data of class ${data::class}")
        }
    }

    class LiveReplayingListener: GameListener {

        lateinit var setupState: FlowStep.GameSetup<Any>
        var lastAction: FlowStep.ActionPerformed<Any>? = null

        override suspend fun handle(coroutineScope: CoroutineScope, step: FlowStep) {
            println("Live replay handling $step")
            if (step is FlowStep.PreSetup<*>) {
                step.state.clear()
                step.state.putAll(setupState.state)
            }
            if (step is FlowStep.PreMove) {
                step.state.clear()
                step.state.putAll(lastAction!!.state)
            }
        }

    }

}